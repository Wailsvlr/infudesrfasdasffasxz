package main

import (
	"encoding/json"
	"fmt"
	"os"
	"strconv"
)

type StaticOffsets struct {
	AmongUsClient uintptr
	GameData      uintptr
}

type FieldOffsets struct {
	// GameData → allPlayers list
	GD_AllPlayers uintptr
	// Il2CppList<T>
	List_Size  uintptr
	List_Items uintptr
	// Il2CppArray
	Array_FirstElement uintptr
	// GameData.PlayerInfo
	PI_PlayerName    uintptr
	PI_PlayerId      uintptr
	PI_ColorId       uintptr
	PI_RoleType      uintptr
	PI_IsDead        uintptr
	PI_Disconnected  uintptr
	PI_Object        uintptr // -> PlayerControl
	// PlayerControl
	PC_MyPhysics uintptr
	PC_NetTransform uintptr
	// CustomNetworkTransform (position)
	NT_TargetPos uintptr // Vector2 (x,y float32)
}

type Config struct {
	Statics StaticOffsets
	Fields  FieldOffsets
	Scanned bool

	// overlay settings
	ESPEnabled      bool
	AlertsEnabled   bool
	MinimapEnabled  bool
	UpdateRateMs    int
}

func DefaultConfig() *Config {
	return &Config{
		Fields: FieldOffsets{
			GD_AllPlayers:    0x44,
			List_Size:        0x18,
			List_Items:       0x10,
			Array_FirstElement: 0x20,
			PI_PlayerName:    0x10,
			PI_PlayerId:      0x18,
			PI_ColorId:       0x20,
			PI_RoleType:      0x2C,
			PI_IsDead:        0x30,
			PI_Disconnected:  0x34,
			PI_Object:        0x38,
			PC_MyPhysics:     0x68,
			PC_NetTransform:  0x70,
			NT_TargetPos:     0x50,
		},
		ESPEnabled:    true,
		AlertsEnabled: true,
		MinimapEnabled: false,
		UpdateRateMs:  100,
		Scanned:       false,
	}
}

func (c *Config) NeedsScan() bool {
	return c.Statics.AmongUsClient == 0
}

// ── JSON persistence ─────────────────────────────────────────────────────────

type cfgJSON struct {
	Version          string            `json:"version"`
	Note             string            `json:"note"`
	Statics          map[string]string `json:"statics"`
	Fields           map[string]string `json:"fields"`
	ESPEnabled       bool              `json:"esp_enabled"`
	AlertsEnabled    bool              `json:"alerts_enabled"`
	MinimapEnabled   bool              `json:"minimap_enabled"`
}

func hexStr(v uintptr) string { return fmt.Sprintf("0x%X", v) }

func parseHex(s string) uintptr {
	s = s
	if len(s) > 2 && s[:2] == "0x" {
		s = s[2:]
	}
	v, _ := strconv.ParseUint(s, 16, 64)
	return uintptr(v)
}

func LoadConfig() *Config {
	cfg := DefaultConfig()

	data, err := os.ReadFile("offsets.json")
	if err != nil {
		return cfg
	}

	var j cfgJSON
	if json.Unmarshal(data, &j) != nil {
		return cfg
	}

	if v, ok := j.Statics["AmongUsClient.Instance"]; ok {
		cfg.Statics.AmongUsClient = parseHex(v)
	}
	if v, ok := j.Statics["GameData.Instance"]; ok {
		cfg.Statics.GameData = parseHex(v)
	}

	fieldMap := map[string]*uintptr{
		"GameData.AllPlayers":          &cfg.Fields.GD_AllPlayers,
		"PlayerInfo.PlayerName":        &cfg.Fields.PI_PlayerName,
		"PlayerInfo.PlayerId":          &cfg.Fields.PI_PlayerId,
		"PlayerInfo.ColorId":           &cfg.Fields.PI_ColorId,
		"PlayerInfo.RoleType":          &cfg.Fields.PI_RoleType,
		"PlayerInfo.IsDead":            &cfg.Fields.PI_IsDead,
		"PlayerInfo.Disconnected":      &cfg.Fields.PI_Disconnected,
		"PlayerInfo.Object":            &cfg.Fields.PI_Object,
		"PlayerControl.MyPhysics":      &cfg.Fields.PC_MyPhysics,
		"PlayerControl.NetTransform":   &cfg.Fields.PC_NetTransform,
		"NetTransform.TargetPos":       &cfg.Fields.NT_TargetPos,
		"List.Size":                    &cfg.Fields.List_Size,
		"List.Items":                   &cfg.Fields.List_Items,
		"Array.FirstElement":           &cfg.Fields.Array_FirstElement,
	}
	for k, ptr := range fieldMap {
		if v, ok := j.Fields[k]; ok {
			*ptr = parseHex(v)
		}
	}

	cfg.ESPEnabled = j.ESPEnabled
	cfg.AlertsEnabled = j.AlertsEnabled
	cfg.MinimapEnabled = j.MinimapEnabled
	cfg.Scanned = cfg.Statics.AmongUsClient != 0

	return cfg
}

func (c *Config) Save() error {
	j := cfgJSON{
		Version: "auto",
		Note:    "otomatik tarama ile olusturuldu — elle degistirilebilir",
		Statics: map[string]string{
			"AmongUsClient.Instance": hexStr(c.Statics.AmongUsClient),
			"GameData.Instance":      hexStr(c.Statics.GameData),
		},
		Fields: map[string]string{
			"GameData.AllPlayers":        hexStr(c.Fields.GD_AllPlayers),
			"PlayerInfo.PlayerName":      hexStr(c.Fields.PI_PlayerName),
			"PlayerInfo.PlayerId":        hexStr(c.Fields.PI_PlayerId),
			"PlayerInfo.ColorId":         hexStr(c.Fields.PI_ColorId),
			"PlayerInfo.RoleType":        hexStr(c.Fields.PI_RoleType),
			"PlayerInfo.IsDead":          hexStr(c.Fields.PI_IsDead),
			"PlayerInfo.Disconnected":    hexStr(c.Fields.PI_Disconnected),
			"PlayerInfo.Object":          hexStr(c.Fields.PI_Object),
			"PlayerControl.MyPhysics":    hexStr(c.Fields.PC_MyPhysics),
			"PlayerControl.NetTransform": hexStr(c.Fields.PC_NetTransform),
			"NetTransform.TargetPos":     hexStr(c.Fields.NT_TargetPos),
			"List.Size":                  hexStr(c.Fields.List_Size),
			"List.Items":                 hexStr(c.Fields.List_Items),
			"Array.FirstElement":         hexStr(c.Fields.Array_FirstElement),
		},
		ESPEnabled:     c.ESPEnabled,
		AlertsEnabled:  c.AlertsEnabled,
		MinimapEnabled: c.MinimapEnabled,
	}

	data, err := json.MarshalIndent(j, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile("offsets.json", data, 0644)
}
