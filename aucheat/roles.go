package main

import (
	"fmt"
	"strings"
)

type Team uint8

const (
	TeamUnknown Team = iota
	TeamCrew
	TeamImpostor
)

type RoleDef struct {
	Name string
	Team Team
}

// RoleTable maps the raw RoleTypes enum value read from PlayerInfo to a name and team.
type RoleTable map[uint32]RoleDef

// DefaultRoleTable is ASSUMED, not taken from a dump of the user's build.
// Verify every entry against RoleTypes in the Il2CppDumper output (dump.cs)
// and override through the "roles" section of offsets.json.
func DefaultRoleTable() RoleTable {
	return RoleTable{
		0: {"Crewmate", TeamCrew},
		1: {"Impostor", TeamImpostor},
		2: {"Scientist", TeamCrew},
		3: {"Engineer", TeamCrew},
		4: {"GuardianAngel", TeamCrew},
		5: {"Shapeshifter", TeamImpostor},
		6: {"Noisemaker", TeamCrew},
		7: {"Tracker", TeamCrew},
		8: {"Phantom", TeamImpostor},
	}
}

// Lookup never invents a mapping: a value missing from the table keeps its raw
// number in the name and gets TeamUnknown.
func (t RoleTable) Lookup(raw uint32) RoleDef {
	if d, ok := t[raw]; ok {
		return d
	}
	return RoleDef{Name: fmt.Sprintf("ROL#%d", raw), Team: TeamUnknown}
}

func teamString(t Team) string {
	switch t {
	case TeamCrew:
		return "crew"
	case TeamImpostor:
		return "impostor"
	}
	return "unknown"
}

func parseTeam(s string) Team {
	switch strings.ToLower(strings.TrimSpace(s)) {
	case "crew":
		return TeamCrew
	case "impostor":
		return TeamImpostor
	}
	return TeamUnknown
}
