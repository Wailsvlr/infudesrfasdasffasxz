package main

import "testing"

func TestLookupKnownAndUnknown(t *testing.T) {
	tbl := DefaultRoleTable()

	if d := tbl.Lookup(1); d.Team != TeamImpostor || d.Name != "Impostor" {
		t.Fatalf("raw 1: got %+v", d)
	}
	if d := tbl.Lookup(0); d.Team != TeamCrew {
		t.Fatalf("raw 0: got %+v", d)
	}
	d := tbl.Lookup(99)
	if d.Team != TeamUnknown || d.Name != "ROL#99" {
		t.Fatalf("raw 99 must stay unknown with its raw number, got %+v", d)
	}
}

func TestParseTeam(t *testing.T) {
	cases := map[string]Team{
		"crew": TeamCrew, " Impostor ": TeamImpostor, "": TeamUnknown, "x": TeamUnknown,
	}
	for in, want := range cases {
		if got := parseTeam(in); got != want {
			t.Fatalf("parseTeam(%q)=%v want %v", in, got, want)
		}
	}
}
