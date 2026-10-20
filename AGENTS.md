Read design-handoff/HANDOFF.md fully before writing any UI code. It is the source of truth for the UI.
Copy design-handoff/theme/*.kt into the app's ui/theme package and fix the package names.
Build the screens in this order: theme + shared components (GameCard, ChannelRow, buttons, tabs, LIVE/PLAYING chips), then S1, S2, S3, S4, S5 + O1, S6, S7.
Every px value in design-handoff/design/*.dc.html is canvas px; divide by 2 for dp/sp.
Respect the hard rules: yellow only for focus and LIVE, pink only as a fill for active state, no gradients, shadows or blur.
After each screen, list any place you deviated from the spec and why.