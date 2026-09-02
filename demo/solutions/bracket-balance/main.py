s = input().strip()

pairs = {")": "(", "]": "[", "}": "{"}
open_stack = []
ok = True
for c in s:
    if c in "([{":
        open_stack.append(c)
    else:
        if not open_stack or open_stack.pop() != pairs[c]:
            ok = False
            break
print("YES" if ok and not open_stack else "NO")
