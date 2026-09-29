--!strict
-- Tiny test harness for Lune.

local H = {}

local results = { passed = 0, failed = 0, failures = {} :: { string } }
local current = ""

function H.section(name: string)
	current = name
	print("\n== " .. name)
end

function H.test(name: string, fn: () -> ())
	local ok, err = pcall(fn)
	if ok then
		results.passed += 1
		print("  ok   " .. name)
	else
		results.failed += 1
		local line = current .. " / " .. name .. ": " .. tostring(err)
		table.insert(results.failures, line)
		print("  FAIL " .. name .. "\n       " .. tostring(err))
	end
end

function H.eq(actual: any, expected: any, msg: string?)
	if actual ~= expected then
		error(string.format("%sexpected %s, got %s", if msg then msg .. ": " else "", tostring(expected), tostring(actual)), 2)
	end
end

function H.near(actual: number, expected: number, tolerance: number, msg: string?)
	if math.abs(actual - expected) > tolerance then
		error(string.format("%sexpected %s ± %s, got %s", if msg then msg .. ": " else "", tostring(expected), tostring(tolerance), tostring(actual)), 2)
	end
end

function H.truthy(value: any, msg: string?)
	if not value then
		error(msg or "expected a truthy value", 2)
	end
end

function H.results()
	return results
end

return H
