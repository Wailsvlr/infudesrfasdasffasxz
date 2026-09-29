-- Runs every automated check that can run outside Roblox Studio.
--   lune run tests/run.luau        (from the MidnightDelivery folder, after building)

local process = require("@lune/process")
local H = require("./harness")

require("./logic.spec")()
require("./service.spec")()
require("./place.spec")()

local r = H.results()
print(string.format("\n%d passed, %d failed", r.passed, r.failed))
for _, f in r.failures do
	print("  - " .. f)
end
process.exit(if r.failed == 0 then 0 else 1)
