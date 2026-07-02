# Nemiskacat — Design System

> *Az ecsetet Te fogod, a többit mi adjuk.*
> ("You hold the brush — we give you everything else.")

Nemiskacat ("not just a cat" — a Hungarian pun on a startled cat) is a Hungarian retailer of premium furniture- and wall-paints. Its mission is to make the joy of self-expression and the quality of creation accessible — for everyone who wants to shape their home with their own hands, with love and color. The company is the official Hungarian distributor for **Annie Sloan** and also carries **Fusion Mineral Paint**, **Polyvine** (lacquers, waxes), **Iron Orchid Designs** (IOD furniture décor), and **Zibra** brushes.

The audience is hobby furniture painters, upcyclers and craft-minded homeowners — predominantly women, 30+, urban and rural, who value individuality over mass production and are willing to spend on well-being and a hand-made home.

**Brand attributes:** *Időtálló* (timeless) · *Rendezett* (composed) · *Letisztult* (clear, uncluttered).

## Sources used to build this system

All sources were uploaded by the user; they live in `uploads/`. They are the source of truth.

| File | What's in it |
| --- | --- |
| `uploads/Nemiskacat brandkép.pdf` | The official brand book. Mission, logo formula, color palette w/ Pantone+CMYK+hex, full type scale, brushstroke shapes, icon style, photography direction. **Primary source.** |
| `uploads/Nemiskacat stíluskép.pdf` | A one-page style-image with sample web compositions. Confirms hero treatment, CTA button styling, blog-card style. |
| `uploads/NK piackutatás ajánlat.pdf` | A partner-prospectus for the 2025 market-research project. Useful for tone-of-voice and target-customer detail. |
| `uploads/2025.11.22. NK event hírlevél, fejléc*.png` | Two real e-mail header artworks for the "Ingyenes Karácsonyi Alkotónap" event. |
| `uploads/Mondd el a véleményed és nyerj egyet...png` | The market-research promotion graphic — partner-logo grid in a yellow card. |
| `uploads/NK_Piackutatás nyeremények-2/-3.png` | Two extra research-promo compositions. |
| `uploads/1…4-*-rgb.png` | The brushstroke shape set — long, short, circle, square — each in yellow / warm-gray / white / black. The brushstroke is the brand's signature graphic element. |
| `uploads/*_transparent.png` | The hand-drawn icon set (brush, paint-can, e-mail, eye, phone, lightbulb-idea, cart, house, warehouse, delivery, workshop). |
| `uploads/{n-,nemiskacat-}logo-{sarga,fekete,feher}-rgb.png` | The wordmark (`nemiskacat` in Lora) and the secondary "n" stamp, each in yellow / black / white. |

> **No codebase or Figma was attached.** If a production codebase, a Figma library, or higher-resolution photography exists, please connect it via the Import menu — anything in those sources should override what's inferred here.

## Index

Everything lives at the project root unless noted. The Design System tab renders the cards under `preview/` automatically.

- `README.md` — you are here.
- `colors_and_type.css` — CSS custom-property tokens for color + type + spacing + radius + shadow + motion.
- `SKILL.md` — invocation file so this folder works as an Agent Skill in Claude Code.
- `assets/`
  - `logos/` — wordmark + "n" stamp in yellow / black / white (PNG).
  - `brushes/` — the brushstroke shape set in every tonal variant.
  - `icons/` — the hand-drawn icon set (PNG, transparent).
  - `examples/` — real production artwork (e-mail headers, research promos).
- `preview/` — the card files that populate the Design System tab.
- `ui_kits/marketing-site/` — a clickable recreation of the public website.
- `uploads/` — original sources, untouched.

---

## 1. Content Fundamentals

The brand's voice is **warm, knowledgeable, encouraging** — the voice of a friend with a paintbrush. Hungarian is the only language used in customer-facing copy.

### Tone & posture
- **Inclusive, second-person.** "Te" / "Téged" (informal *you*) — never "Ön" (formal). The reader is treated as a friend who is about to start a project. Example tagline: *"Az ecsetet Te fogod, a többit mi adjuk!"* — note the capitalised "Te" (capital T is a Hungarian convention for respect, kept here even in the casual register).
- **"Mi" (we) over "I".** The brand speaks as a small team / community. From the market-research letter: "*Mi is sokféle eszközt próbálunk bevetni, hogy előrébb jussunk.*" ("We also try many tools to move forward.")
- **Invitational, never instructional.** Buttons say *"Megyek olvasni, inspirálódni"* ("I'm going to read and get inspired") — phrased from the reader's POV. Compare to a generic "Read more".
- **Quiet confidence, not hype.** No exclamation marks stacked, no all-caps, no "AMAZING!!!". When excitement appears, it's a single soft exclamation: *"Újdonság!"*

### Casing
- Sentence case for headlines and buttons. Title Case never appears.
- The wordmark itself is lowercase — `nemiskacat`, always one word, never capitalised, even at the start of a sentence in marketing copy.
- Hungarian capitalisation of polite pronouns (Te, Téged, Ti) is preserved.

### Microcopy patterns
| Where | Pattern | Example |
| --- | --- | --- |
| Hero | Question / promise in Caveat script on a brushstroke | *"Az ecsetet Te fogod, a többit mi adjuk!"* |
| Section eyebrow | Short noun phrase in Lora | *"Festékek webshop"*, *"Bútorfelújítás tudásanyagok"* |
| Body | Long-form, conversational, explanatory | *"Idővel a legszebb asztalka vagy szekrény is megkopik…"* |
| Button | First-person verb phrase | *"Elolvasom"*, *"Megyek olvasni, inspirálódni"*, *"Termékek a webshopban"* |
| Highlight chip | Single word on a circular brushstroke | *"Újdonság!"*, *"Nézd meg"* |
| Quote | Long pull-quote in Lora italic | Customer testimonial about Annie Sloan |

### What the brand does NOT do
- **No emoji.** None of the source material uses them. The hand-drawn icon set is the brand's emoji.
- **No marketing clichés.** No "transform your life", no "unleash your creativity". The promise is concrete: *paint a chair, paint a wall.*
- **No urgency / scarcity tactics.** No countdowns, no "only 3 left!". The brand is calm and timeless.
- **No tech jargon.** "Workshop" appears, "blog" appears, but the rest is plain Hungarian.

---

## 2. Visual Foundations

The visual world is **bright, generous and quiet**, with a single warm yellow accent that does all the heavy lifting. Think *atelier on a sunny morning* — not boutique-luxury, not folksy-craft.

### Color
The system has **four primary colors and three accent stops**, period.

| Token | Hex | Role |
| --- | --- | --- |
| `--nk-white` | `#ffffff` | The dominant surface. White is *the* color. |
| `--nk-gray-20` | `#EBE6E3` | Warm gray — the only "second background". Breaks up white without going cold. |
| `--nk-black` | `#000000` | All text and outlines. |
| `--nk-yellow-50` | `#ECC45D` | The accent. Used on buttons, brushstrokes behind slogans, and the "n" stamp. |
| `--nk-yellow-40` | `#F5DFA1` | Soft fill — backgrounds for cards / soft highlights. |
| `--nk-yellow-60` | `#C39E36` | Pressed / strong yellow — link hovers, button :active. |

Bluish purples, teals, neon greens, gradients with multiple hues — **none of these belong**. The only photo-led colors that appear are the real paint colors of Annie Sloan furniture in lifestyle imagery, never as UI color.

### Typography
- **Lora** (serif) for everything readable: headings, body, buttons sometimes, captions. Weights used: 400 (Regular) and 500–700 (Medium → Bold). The brand book emphasises the `s‑k` ligature in the wordmark; on the web we let Lora's defaults handle it. Loaded locally from `/fonts/`.
- **Caveat** (handwritten script) is the *emotional* layer. It loads locally from the brand-supplied files in `/fonts/` (Regular 400 / Medium 500 / SemiBold 600 / Bold 700). It appears **only**:
  - On brushstroke graphics (slogans, promises).
  - In CTA button labels (e.g. *Elolvasom*).
  - As small "Újdonság!" / "Nézd meg" badges.
  Never as body copy, never as a paragraph, never below 22pt.
- Both Lora and Caveat load locally from `/fonts/`. No external font dependencies.

### Spacing & layout
- **Generous whitespace is itself a brand element.** The brand book explicitly calls white "the bountiful base that lets material textures breathe". Cards and sections have wide outer padding (≥ 64px on desktop) and short text columns.
- Layout is **centered, calm and orthogonal**. No skewed grids, no asymmetric collages.
- Hero compositions follow a two-band pattern: a Caveat-on-brushstroke promise on the left, lifestyle photo on the right.

### Backgrounds
- **Plain white** is the default canvas.
- **Warm gray (`#EBE6E3`)** is the only secondary canvas, used to delineate sections.
- **Full-bleed photography** appears for hero and feature blocks — only the brand's own photography or Annie Sloan campaign imagery.
- **No gradients.** No mesh, no radial bleed, no glassmorphism.
- **No repeating patterns or textures** in flat UI. The only texture in the system is the *brushstroke graphic itself*, which is treated as a discrete element.

### Brushstrokes (the signature graphic)
- Four shape families: **long ribbon** (one-line messages), **short rectangle** (multi-line slogans), **round circle** (one-word badges like *Újdonság!*), **square stamp** (used as the carrier for the "n" mark).
- Four tonal variants per shape: yellow, warm gray, white (for use on tone backgrounds), black (for high-contrast posters).
- Brushstrokes are **never resized non-uniformly**, never tinted to a non-system color, never used as decoration without text on top.
- Usage pairing:
  - white background → yellow or gray brush
  - yellow/tone background → white or yellow brush
  - dark photo → yellow or white brush

### Borders, corners, elevation
- **Corners are softly rounded** — 12px on cards, 20px on large surfaces, 999px on small chips / brushstroke buttons. No 0px sharp corners except inside data tables.
- **Borders are 1px hairlines in `--nk-gray-30`** on cards and form fields. The high-contrast "stamped" black outline lives only in icons and the hand-drawn brand assets.
- **Shadows are warm and soft.** Tokens: `--nk-shadow-xs` for hover lift, `--nk-shadow-sm` for cards at rest, `--nk-shadow-md` for menus, `--nk-shadow-lg` for modals. The shadow color is warm brown-gray, never cool blue.
- No inner shadows, no neumorphism.
- No "left-border accent" cards (the cliché where a card has a colored 4-px stripe on the left). We've seen no example of this in source material; do not introduce it.

### Animation & interaction states
- **Easing is `cubic-bezier(0.22, 0.61, 0.36, 1)`** — a gentle quick-out, settle-in. No bouncy springs. No anticipation.
- **Durations:** 120ms for micro-interactions (hover), 220ms for transitions (panel reveal), 360ms for page-level changes. Nothing longer than 360ms in UI.
- **Hover:** yellow buttons darken to `--nk-yellow-60`. Outline/text links pick up a 2-px Caveat-styled underline (in `--nk-yellow-60`). No scale, no glow.
- **Pressed (`:active`):** 2-px downward translate + slight darkening. No "press shrink to 0.96".
- **Focus:** 3-px `--nk-yellow-50` ring with 2-px offset. Always visible — accessibility is non-negotiable.
- Cards do **not** lift on hover by default; only interactive cards (links / CTAs) do, and the lift is a 2-px translate + bump to `--nk-shadow-md`.

### Layout rules
- Fixed top nav, transparent on hero, sticks-and-fills-white on scroll.
- Footer is **warm gray (`--nk-gray-20`)** with black text — the only section that breaks the white default.
- Containers max-width: 1200px for content, 1400px for full-bleed hero.
- Mobile breakpoint: 768px. The hand-drawn icons and brushstroke graphics rescale beautifully down to phone — they're PNGs with transparency at ~2× retina.

### Use of transparency / blur
- **Almost none.** No frosted glass, no backdrop-filter blur.
- The only transparency in the system is the PNG transparency of the brushstroke and icon assets themselves.

### Imagery direction
- **Warm and bright**, never moody, never cool. The brand book demands "natural side-light" for portraits and lifestyle.
- Two photo families:
  1. **Annie Sloan official imagery** — wide interior compositions, painted furniture in styled rooms.
  2. **Nemiskacat's own** — close-up, detail-rich, hands-and-materials. Brush dipped in paint, swatches on wood.
- Workshop photos: phone-shot, candid, side-lit. *"A pillanat valódisága számít a tökéletesség helyett."*
- Before/after pairs: same composition, same background, only the painted object changes.

---

## 3. Iconography

The brand has its own **bespoke hand-drawn icon set**, delivered as transparent PNGs. The drawing is loose, energetic, with a thick "marker on watercolor paper" feel — not vector-perfect.

### What's in the set (in `assets/icons/`)

| File | Subject | Suggested use |
| --- | --- | --- |
| `brush.png` | Paintbrush | Anything painting-related |
| `paint-can.png` | Paint bucket with a drip | Products, shop |
| `cart.png` | Shopping basket | Cart, checkout |
| `house.png` | A little gabled house | Home, contact, address |
| `warehouse.png` | A barn-like building | "Raktár" — in-stock, warehouse |
| `delivery.png` | A delivery truck | Shipping |
| `workshop.png` | Three figures together | Workshops, community |
| `email.png` | The @-symbol drawn freehand | Email, contact |
| `phone.png` | A phone handset | Phone, contact |
| `eye.png` | Eye with lashes | View / preview |
| `idea.png` | Lightbulb with a "?" inside | Tips, ideas, FAQ |

### Rules of use
- **Always use the PNG** — never re-draw or convert to a vector. The brand owner's hand is in the linework.
- **Icons are dark line on light surfaces** by default. When placed on yellow or warm-gray, that's fine — the linework is heavy enough to read.
- **Icon sizing:** 24–32px in inline UI (next to a label), 48–64px in feature blocks, 96px+ as a hero illustration.
- **Pair with text — always.** The icons are stylistic, not always immediately legible; a label removes ambiguity.
- **Don't replace** with Lucide / Heroicons / Material. The visual personality breaks instantly. If a needed icon is missing from this set, **flag it** (see "Caveats" below) rather than substituting.
- **No emoji.** Emoji are not part of the brand. The hand-drawn icons fill that role.
- **No Unicode "icons"** (★, ✓, ♥) used decoratively. If a checkmark or arrow is genuinely needed inside a button or row, use a thin black line drawn in CSS, *not* a Unicode glyph.

### What we did NOT load from a CDN
We considered Lucide / Heroicons as a fallback for the "missing" icons but **deliberately did not include them** — they'd clash with the brand's hand-drawn vocabulary. If you need an icon that isn't in `assets/icons/`, ask the brand owner to draw one in the same style.

---

## Caveats & open questions

- **Fonts:** Both **Lora** (Regular / Italic / Medium / Medium Italic / SemiBold / SemiBold Italic / Bold / Bold Italic) and **Caveat** (Regular / Medium / SemiBold / Bold) load locally from `/fonts/` using the brand-supplied font files. No external font dependencies.
- **No production codebase** was attached. The marketing-site UI kit recreates the *visible* style of nemiskacat.hu and the brand book — it is not a 1:1 component copy. If you have a Figma library or codebase, please attach it.
- **Color tokens `gray-10` and `gray-30`** are inferred. The brand book lists "Meleg szürke 10/20/30" but the printed hex values for 10 and 30 appear to be typos (they show `#EBE6E3` and `#ECC45D` respectively, which collide with gray-20 and yellow-50). I've used a sensible scale; please confirm or correct.
- **Icon coverage is small.** Eleven hand-drawn icons exist. A real product surface will need more (search, menu, close, chevrons, settings, etc.). The right move is to commission more in the same hand, not to mix in a vector set.
- **Photography:** No actual brand photos are included in this system — they live on the production CMS. The UI kit uses neutral placeholders; please drop real photography into `assets/photos/` and we'll wire it in.
