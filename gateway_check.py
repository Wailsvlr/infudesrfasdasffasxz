#!/usr/bin/env python3
"""Gateway baglanti testi + gateway kesfi + icerik listeleme.

Bu arac site veya dosya guvenligini DOGRULAMAZ.

Kullanim:
  python gateway_check.py
  python gateway_check.py --discover-gateways https://ornek.example/gateways.json
  python gateway_check.py --discover-gateways gateways.txt --list-content

Yalnizca standart kutuphane. Dosya yazmaz, indirilen hicbir seyi calistirmaz,
yonlendirmeleri (redirect) takip etmez, listelenen baglantilari acmaz.
Colab: !python gateway_check.py --list-content
"""

import argparse
import concurrent.futures
import html.parser
import http.client
import ipaddress
import json
import re
import socket
import ssl
import time
import urllib.parse

# --- Yapilandirma -----------------------------------------------------------
# Kaynak: onceki gateway_check.py icindeki sabit HOSTS listesi (kullanici dosyasi).
DEFAULT_NAME = "cyberleek"
SEED_BASES = ("ardrive.net", "arweave.net", "permaweb.io", "arns.app")

# Gateway listesi kaynagi BILINMIYOR: varsayilan bir URL uydurulmadi.
# --discover-gateways ile verilir (https URL veya yerel dosya).
# Ayristirma varsayimi (DOGRULANMADI): JSON dizisi icinde string'ler veya
# asagidaki anahtarlardan birini tasiyan nesneler; veya satir satir metin.
GATEWAY_RECORD_KEYS = ("fqdn", "host", "domain")

MAX_LIST_BYTES = 1 << 20
MAX_BODY_DEFAULT = 256 * 1024
MAX_BODY_LIMIT = 4 * 1024 * 1024
MAX_ITEMS = 50
MAX_SCAN = 2000
MAX_GATEWAYS_DEFAULT = 40

HOST_RE = re.compile(r"^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$")
NAME_RE = re.compile(r"^[a-z0-9](?:[a-z0-9-]{0,50}[a-z0-9])?$")
TXID_RE = re.compile(r"^[A-Za-z0-9_-]{43}$")
CONTROL_RE = re.compile(r"[\x00-\x1f\x7f-\x9f]")


# --- Saf fonksiyonlar (oyunsuz/agsiz test edilebilir) -----------------------
def valid_host(host):
    return isinstance(host, str) and HOST_RE.match(host) is not None


def clean(text, limit=200):
    return CONTROL_RE.sub(" ", str(text)).strip()[:limit]


def build_hosts(name, bases):
    return [name + "." + base for base in bases]


def normalize_gateway_entry(entry):
    """Bir kayittan (str veya dict) kucuk harfli, gecerli alan adi dondurur; yoksa None."""
    if isinstance(entry, dict):
        for key in GATEWAY_RECORD_KEYS:
            if isinstance(entry.get(key), str):
                entry = entry[key]
                break
        else:
            return None
    if not isinstance(entry, str):
        return None
    text = entry.strip().lower()
    if "://" in text:
        try:
            text = urllib.parse.urlsplit(text).hostname or ""
        except ValueError:
            return None
    text = text.rstrip(".")
    return text if valid_host(text) else None


def parse_gateway_list(text):
    """Metinden (JSON dizisi veya satir satir) taban alan adlari cikarir."""
    entries = None
    stripped = text.strip()
    if stripped[:1] in ("[", "{"):
        try:
            data = json.loads(stripped)
        except (ValueError, RecursionError):
            data = None
        if isinstance(data, list):
            entries = data
        elif isinstance(data, dict):
            for key in ("gateways", "items", "hosts"):
                if isinstance(data.get(key), list):
                    entries = data[key]
                    break
    if entries is None:
        entries = [line for line in stripped.splitlines()
                   if line.strip() and not line.lstrip().startswith("#")]
    bases, rejected = [], 0
    for entry in entries:
        host = normalize_gateway_entry(entry)
        if host is None:
            rejected += 1
        elif host not in bases:
            bases.append(host)
    return bases, rejected


def merge_candidates(name, seed_bases, discovered_bases, limit):
    """Sabit tohum her zaman once gelir; kesfedilenler sinira kadar eklenir."""
    hosts = build_hosts(name, seed_bases)
    sources = {host: "seed" for host in hosts}
    for base in discovered_bases:
        host = name + "." + base
        if host in sources or not valid_host(host):
            continue
        if len(hosts) >= limit:
            break
        hosts.append(host)
        sources[host] = "discovered"
    return hosts, sources


class _PageScanner(html.parser.HTMLParser):
    _ATTR = {"a": "href", "link": "href", "script": "src", "img": "src",
             "iframe": "src", "source": "src", "form": "action"}

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.title = ""
        self.refs = []
        self._in_title = False

    def handle_starttag(self, tag, attrs):
        if tag == "title":
            self._in_title = True
        key = self._ATTR.get(tag)
        if key and len(self.refs) < MAX_SCAN:
            value = dict(attrs).get(key)
            if value:
                self.refs.append((tag, value))

    def handle_endtag(self, tag):
        if tag == "title":
            self._in_title = False

    def handle_data(self, data):
        if self._in_title and len(self.title) < 200:
            self.title += data


def summarize_html(text, host):
    scanner = _PageScanner()
    try:
        scanner.feed(text)
        scanner.close()
    except Exception:
        pass
    base = "https://" + host + "/"
    refs, seen = [], set()
    for tag, raw in scanner.refs:
        ref = clean(raw, 300)
        try:
            absolute = urllib.parse.urljoin(base, ref)
            parts = urllib.parse.urlsplit(absolute)
        except ValueError:
            continue
        if absolute in seen:
            continue
        seen.add(absolute)
        if parts.scheme not in ("http", "https"):
            scope = "other_scheme"
        elif (parts.hostname or "") == host:
            scope = "same_host"
        else:
            scope = "external"
        refs.append({"tag": tag, "url": clean(absolute, 300), "scope": scope})
    return {"kind": "html", "title": clean(scanner.title),
            "reference_count": len(refs), "references": refs[:MAX_ITEMS]}


def summarize_manifest(data):
    paths = data.get("paths")
    out = {"kind": "arweave_path_manifest",
           "version": clean(data.get("version", "")),
           "path_count": len(paths) if isinstance(paths, dict) else 0,
           "paths": []}
    index = data.get("index")
    if isinstance(index, dict) and isinstance(index.get("path"), str):
        out["index_path"] = clean(index["path"])
    if isinstance(paths, dict):
        for path in sorted(paths, key=str)[:MAX_ITEMS]:
            target = paths[path]
            txid = target.get("id") if isinstance(target, dict) else None
            out["paths"].append({
                "path": clean(path),
                "id": txid if isinstance(txid, str) and TXID_RE.match(txid) else None,
            })
    return out


def summarize_content(content_type, body, truncated, host):
    media = content_type.split(";")[0].strip().lower()
    text = body.decode("utf-8", errors="replace")
    head = text.lstrip()[:1]
    if head in ("{", "[") or "json" in media:
        if truncated:
            return {"kind": "json_truncated_not_parsed", "bytes_read": len(body)}
        try:
            data = json.loads(text)
        except (ValueError, RecursionError):
            return {"kind": "json_invalid", "bytes_read": len(body)}
        if isinstance(data, dict) and data.get("manifest") == "arweave/paths":
            result = summarize_manifest(data)
        elif isinstance(data, dict):
            result = {"kind": "json_object",
                      "keys": [clean(k, 80) for k in list(data)[:MAX_ITEMS]]}
        else:
            result = {"kind": "json_other"}
    elif "html" in media or "<" in text[:2048]:
        result = summarize_html(text, host)
    else:
        result = {"kind": "other"}
    result["content_type"] = clean(content_type)
    result["bytes_read"] = len(body)
    result["truncated"] = truncated
    return result


# --- Ag katmani -------------------------------------------------------------
def public_addresses(host):
    if not valid_host(host):
        raise ValueError("Host is not a valid public DNS name")
    records = socket.getaddrinfo(host, 443, type=socket.SOCK_STREAM)
    approved = []
    for family, kind, protocol, _, address in records:
        ip = ipaddress.ip_address(address[0].split("%")[0])
        if not ip.is_global or getattr(ip, "ipv4_mapped", None):
            raise ValueError("DNS returned a non-public or mapped address")
        record = (family, kind, protocol, address)
        if record not in approved:
            approved.append(record)
    if not approved:
        raise ValueError("DNS returned no addresses")
    return approved


class PinnedHTTPS(http.client.HTTPSConnection):
    def __init__(self, host, address, timeout):
        super().__init__(host, timeout=timeout, context=ssl.create_default_context())
        self.approved_address = address

    def connect(self):
        # Kontrol edilen IP'ye dogrudan baglan; ana bilgisayar adi dogrulamasi ve SNI korunur.
        family, kind, protocol, address = self.approved_address
        raw = socket.socket(family, kind, protocol)
        raw.settimeout(self.timeout)
        try:
            raw.connect(address)
            self.sock = self._context.wrap_socket(raw, server_hostname=self.host)
        except BaseException:
            raw.close()
            raise


def classify_error(exc):
    # SSLCertVerificationError hem OSError hem ValueError alt sinifi: once o.
    if isinstance(exc, ssl.SSLCertVerificationError):
        return "certificate_rejected"
    if isinstance(exc, socket.gaierror):
        return "dns_failed"
    if isinstance(exc, (TimeoutError, socket.timeout)):
        return "timeout"
    if isinstance(exc, ValueError):
        return "address_rejected"
    if isinstance(exc, (OSError, http.client.HTTPException)):
        return "connection_failed"
    return "unexpected_error"


def read_limited(response, limit, deadline):
    chunks, total = [], 0
    while total <= limit:
        if time.monotonic() > deadline:
            raise TimeoutError("total read deadline exceeded")
        chunk = response.read(min(8192, limit + 1 - total))
        if not chunk:
            break
        chunks.append(chunk)
        total += len(chunk)
    data = b"".join(chunks)
    return data[:limit], len(data) > limit


def fetch_text(host, path, timeout, limit):
    """Sinirli GET. Yonlendirme takip edilmez. (status, text) dondurur."""
    conn = None
    try:
        conn = PinnedHTTPS(host, public_addresses(host)[0], timeout)
        conn.request("GET", path, headers={
            "User-Agent": "ConnectionCheck/1.0",
            "Accept": "application/json, text/plain;q=0.9, */*;q=0.1",
            "Accept-Encoding": "identity",
            "Connection": "close",
        })
        response = conn.getresponse()
        if response.status != 200:
            return "http_" + str(response.status), ""
        if response.getheader("Content-Encoding", "identity").lower() != "identity":
            return "unsupported_encoding", ""
        body, truncated = read_limited(response, limit, time.monotonic() + timeout * 3)
        if truncated:
            return "too_large", ""
        return "ok", body.decode("utf-8", errors="replace")
    except Exception as exc:
        return classify_error(exc), ""
    finally:
        if conn:
            conn.close()


def load_gateway_source(source, timeout):
    """https URL veya yerel dosyadan gateway listesi metni okur."""
    if source.lower().startswith("https://"):
        try:
            parts = urllib.parse.urlsplit(source)
            port = parts.port
        except ValueError:
            return "bad_url", ""
        host = (parts.hostname or "").lower()
        if not valid_host(host) or port not in (None, 443) or parts.username:
            return "bad_url", ""
        path = (parts.path or "/") + ("?" + parts.query if parts.query else "")
        return fetch_text(host, path, timeout, MAX_LIST_BYTES)
    if "://" in source:
        return "scheme_rejected", ""
    try:
        with open(source, "rb") as handle:
            data = handle.read(MAX_LIST_BYTES + 1)
    except OSError:
        return "file_unreadable", ""
    if len(data) > MAX_LIST_BYTES:
        return "too_large", ""
    return "ok", data.decode("utf-8", errors="replace")


def fetch_content(host, timeout, max_body):
    conn = None
    try:
        conn = PinnedHTTPS(host, public_addresses(host)[0], timeout)
        conn.request("GET", "/", headers={
            "User-Agent": "ConnectionCheck/1.0",
            "Accept": "text/html, application/json;q=0.9, */*;q=0.1",
            "Accept-Encoding": "identity",
            "Connection": "close",
        })
        response = conn.getresponse()
        info = {"http_status": response.status}
        if 300 <= response.status < 400:
            info["status"] = "redirect_not_followed"
            info["location"] = clean(response.getheader("Location") or "", 300)
            return info
        if not 200 <= response.status < 300:
            info["status"] = "http_error"
            return info
        if response.getheader("Content-Encoding", "identity").lower() != "identity":
            info["status"] = "unsupported_encoding"
            return info
        body, truncated = read_limited(response, max_body, time.monotonic() + timeout * 3)
        info["status"] = "listed"
        info.update(summarize_content(response.getheader("Content-Type") or "",
                                      body, truncated, host))
        return info
    except Exception as exc:
        return {"status": classify_error(exc)}
    finally:
        if conn:
            conn.close()


def check(host, source, timeout, list_content, max_body):
    started = time.monotonic()
    result = {"url": "https://" + host, "source": source, "status": "not_checked"}
    conn = None
    try:
        conn = PinnedHTTPS(host, public_addresses(host)[0], timeout)
        # HEAD yalnizca basliklari ister. Yonlendirmeler raporlanir, takip edilmez.
        conn.request("HEAD", "/", headers={
            "User-Agent": "ConnectionCheck/1.0",
            "Connection": "close",
        })
        response = conn.getresponse()
        result["http_status"] = response.status
        result["tls_verified"] = True
        if 200 <= response.status < 300:
            result["status"] = "responded_content_not_verified"
        elif 300 <= response.status < 400:
            result["status"] = "redirect_not_followed"
        elif response.status == 405:
            result["status"] = "head_not_supported_content_not_checked"
        else:
            result["status"] = "http_error"
        response.close()
    except Exception as exc:
        result["status"] = classify_error(exc)
    finally:
        if conn:
            conn.close()
    reachable = result.get("tls_verified") is True
    if list_content and reachable:
        result["content"] = fetch_content(host, timeout, max_body)
    result["elapsed_ms"] = round((time.monotonic() - started) * 1000)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--timeout", type=int, choices=range(2, 11), default=6,
                        help="Socket timeout in seconds (2-10); OS DNS may take longer")
    parser.add_argument("--name", default=DEFAULT_NAME,
                        help="ArNS adi (alt alan adi); varsayilan: " + DEFAULT_NAME)
    parser.add_argument("--discover-gateways", action="append", default=[], metavar="URL_OR_FILE",
                        help="Gateway taban alan adi listesi (https URL veya dosya); tekrarlanabilir")
    parser.add_argument("--max-gateways", type=int, default=MAX_GATEWAYS_DEFAULT,
                        help="Kontrol edilecek en fazla host (tohum dahil), 1-200")
    parser.add_argument("--list-content", action="store_true",
                        help="Ulasilan hostlarda '/' icerigini sinirli okuyup ozetle")
    parser.add_argument("--max-body", type=int, default=MAX_BODY_DEFAULT,
                        help="--list-content icin en fazla bayt (4096-4194304)")
    args = parser.parse_args()
    if not NAME_RE.match(args.name):
        parser.error("--name gecerli bir alt alan adi etiketi olmali")
    if not 1 <= args.max_gateways <= 200:
        parser.error("--max-gateways 1-200 araliginda olmali")
    if not 4096 <= args.max_body <= MAX_BODY_LIMIT:
        parser.error("--max-body 4096-4194304 araliginda olmali")

    discovered, source_reports = [], []
    for source in args.discover_gateways:
        status, text = load_gateway_source(source, args.timeout)
        report = {"source": clean(source, 300), "status": status}
        if status == "ok":
            bases, rejected = parse_gateway_list(text)
            report.update(accepted=len(bases), rejected=rejected)
            discovered.extend(base for base in bases if base not in discovered)
        source_reports.append(report)

    hosts, origin = merge_candidates(args.name, SEED_BASES, discovered, args.max_gateways)

    print("yalnizca baglanti testi; site veya dosya guvenligi dogrulanmaz.")
    print(str(len(hosts)) + " sunucuya istek gonderilir; sunucular baglanti IP adresini gorebilir.")
    if args.discover_gateways:
        print("gateway listesi kaynaklari okundu; kesfedilen adaylar yalnizca genel IP ve "
              "gecerli TLS sertifikasi sartiyla kontrol edilir.")
    if args.list_content:
        print("icerik listeleme: her hosttan en fazla " + str(args.max_body) +
              " bayt okunur; hicbir sey kaydedilmez, calistirilmaz, baglantilar acilmaz.")

    with concurrent.futures.ThreadPoolExecutor(max_workers=min(8, len(hosts))) as pool:
        futures = [pool.submit(check, host, origin[host], args.timeout,
                               args.list_content, args.max_body) for host in hosts]
        results = [future.result() for future in futures]

    report = {"discovery": {"sources": source_reports, "candidates": len(hosts)},
              "gateways": results}
    print(json.dumps(report, indent=2, ensure_ascii=True))


if __name__ == "__main__":
    main()
