# Balance portraits

24 official character portraits, captured on 2026-10-18 from the `character_image` fields of:

https://klbq-prod-www.idreamsky.com/api/pages/KLBQ_BALANCE/index

The upstream uses two CDNs: `klbq-web-cms.strinova.com` for 22 characters and
`klbqcp-web-cdn.idreamsky.com` for 汐 (130) and 诺诺 (126). Keeping optimized copies
avoids depending on the visitor's access to either CDN.

Files are keyed by immutable character ID, resized to 96×96 and encoded as WebP
(quality 88, alpha retained). The entire set is about 135 KiB. Game artwork belongs
to the game's rights holders; these files are display assets, not original artwork
created by this project.

When refreshing a portrait, preserve its ID. Add new IDs to `src/balanceAvatars.ts`
alongside the asset. Unknown IDs use their official metadata URL automatically;
failed local images fall back to that URL, then to a name initial.
