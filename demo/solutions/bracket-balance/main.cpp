#include <bits/stdc++.h>
using namespace std;

int main() {
    string s;
    cin >> s;

    string open;
    bool ok = true;
    for (char c : s) {
        if (c == '(' || c == '[' || c == '{') {
            open.push_back(c);
        } else {
            char want = c == ')' ? '(' : (c == ']' ? '[' : '{');
            if (open.empty() || open.back() != want) { ok = false; break; }
            open.pop_back();
        }
    }
    cout << (ok && open.empty() ? "YES" : "NO") << "\n";
}
