package main

import (
	"math"
	"sync"
)

// ── player data ──────────────────────────────────────────────────────────────

type PlayerInfo struct {
	Name           string
	PlayerId       byte
	ColorId        int32
	IsImpostor     bool
	IsDead         bool
	IsDisconnected bool
	WorldX         float32
	WorldY         float32
	ScreenX        int32
	ScreenY        int32
	Distance       float32 // distance to local player
	InKillRange    bool
}

type GameState struct {
	mu       sync.RWMutex
	Players  []PlayerInfo
	LocalX   float32
	LocalY   float32
	InGame   bool
	ErrCount int
}

// ── game reader ──────────────────────────────────────────────────────────────

type GameReader struct {
	proc *Proc
	base uintptr
	cfg  *Config
	state GameState
}

func NewGameReader(proc *Proc, base uintptr, cfg *Config) *GameReader {
	return &GameReader{proc: proc, base: base, cfg: cfg}
}

func (g *GameReader) State() *GameState {
	return &g.state
}

const killRange = 1.8 // Among Us default kill distance

func (g *GameReader) Update() {
	g.state.mu.Lock()
	defer g.state.mu.Unlock()

	players, err := g.readPlayers()
	if err != nil {
		g.state.ErrCount++
		if g.state.ErrCount > 10 {
			g.state.InGame = false
			g.state.Players = nil
		}
		return
	}

	g.state.ErrCount = 0
	g.state.Players = players
	g.state.InGame = len(players) > 0
}

func (g *GameReader) readPlayers() ([]PlayerInfo, error) {
	f := &g.cfg.Fields

	// AmongUsClient.Instance → check if in game
	aucPtr, err := g.proc.ReadPtr(g.cfg.Statics.AmongUsClient)
	if err != nil || aucPtr == 0 {
		return nil, err
	}

	// GameData.Instance
	gdPtr, err := g.proc.ReadPtr(g.cfg.Statics.GameData)
	if err != nil || gdPtr == 0 {
		return nil, err
	}

	// GameData.AllPlayers (Il2CppList<PlayerInfo>)
	listPtr, err := g.proc.ReadPtr(gdPtr + f.GD_AllPlayers)
	if err != nil || listPtr == 0 {
		return nil, err
	}

	// list count
	count, err := g.proc.ReadI32(listPtr + f.List_Size)
	if err != nil || count <= 0 || count > 15 {
		return nil, err
	}

	// items array
	arrPtr, err := g.proc.ReadPtr(listPtr + f.List_Items)
	if err != nil || arrPtr == 0 {
		return nil, err
	}

	var players []PlayerInfo
	var localX, localY float32
	foundLocal := false

	for i := int32(0); i < count; i++ {
		piPtr, err := g.proc.ReadPtr(arrPtr + f.Array_FirstElement + uintptr(i)*8)
		if err != nil || piPtr == 0 {
			continue
		}

		pi, ok := g.readPlayerInfo(piPtr)
		if !ok {
			continue
		}

		// read position from PlayerControl -> NetTransform -> TargetPos
		pcPtr, _ := g.proc.ReadPtr(piPtr + f.PI_Object)
		if pcPtr != 0 {
			ntPtr, _ := g.proc.ReadPtr(pcPtr + f.PC_NetTransform)
			if ntPtr != 0 {
				pi.WorldX, _ = g.proc.ReadF32(ntPtr + f.NT_TargetPos)
				pi.WorldY, _ = g.proc.ReadF32(ntPtr + f.NT_TargetPos + 4)
			}
		}

		// detect local player (first non-disconnected, non-dead player typically)
		if !foundLocal && !pi.IsDisconnected && !pi.IsDead {
			localX = pi.WorldX
			localY = pi.WorldY
			foundLocal = true
		}

		players = append(players, pi)
	}

	// compute distances to local player
	if foundLocal {
		g.state.LocalX = localX
		g.state.LocalY = localY
		for i := range players {
			dx := players[i].WorldX - localX
			dy := players[i].WorldY - localY
			players[i].Distance = float32(math.Sqrt(float64(dx*dx + dy*dy)))
			players[i].InKillRange = players[i].Distance <= killRange && !players[i].IsDead
		}
	}

	return players, nil
}

func (g *GameReader) readPlayerInfo(addr uintptr) (PlayerInfo, bool) {
	f := &g.cfg.Fields
	var pi PlayerInfo

	namePtr, err := g.proc.ReadPtr(addr + f.PI_PlayerName)
	if err != nil {
		return pi, false
	}
	pi.Name, _ = g.proc.ReadIL2CppString(namePtr)
	if pi.Name == "" {
		return pi, false
	}

	pid, _ := g.proc.ReadByte(addr + f.PI_PlayerId)
	pi.PlayerId = pid

	pi.ColorId, _ = g.proc.ReadI32(addr + f.PI_ColorId)

	role, _ := g.proc.ReadU32(addr + f.PI_RoleType)
	pi.IsImpostor = (role == 1 || role == 3) // Impostor=1, Shapeshifter=3

	pi.IsDead, _ = g.proc.ReadBool(addr + f.PI_IsDead)
	pi.IsDisconnected, _ = g.proc.ReadBool(addr + f.PI_Disconnected)

	return pi, true
}
