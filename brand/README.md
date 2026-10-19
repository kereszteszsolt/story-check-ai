# StoryCheck AI branding

[`branding.json`](branding.json) follows the shared display-branding structure. Edit project metadata here. It contains no credentials or runtime provider configuration.

| Field | Meaning |
| --- | --- |
| `appName` | Display name |
| `appTagline` | Main introductory heading |
| `appDescription` | Browser metadata and About description |
| `appWebsite` | Project website; empty when none is configured |
| `appGithubUrl` | Project repository |
| `appFavicon` | Optional local favicon |
| `appIcon` | Optional local project mark |
| `appPoster` | Optional local project poster |
| `authorName` | Author display name |
| `authorWebsite` | Author website |
| `authorWebsiteLanguages` | Ordered language codes in website/support tooltips; an empty array omits the language note |
| `authorGithub` | Author GitHub profile |
| `authorWaysToSupportUrl` | General ways to support the author |
| `authorBuyMeACoffeeUrl` | Direct Buy Me a Coffee link |
| `supportRemarkText` | Voluntary-support notice; the second sentence starts on a new line |

Asset paths are relative to this `brand/` directory and must stay inside `assets/`. Empty paths mean no image. These are source paths, not public URLs: consumers must package or map them to their own public base. Do not fetch remote artwork or resolve arbitrary filesystem paths.

The SVG and PNG posters are unchanged copies of `kereszteszsolt/project-cards/story-check-ai-poster.*` from the profile repository. `appPoster` selects the vector version; the PNG is the social-preview alternative. The new `story-check-ai-icon.svg` reuses the poster's cream story sheet, teal magnifying glass/checkmark and gold question bubble. `story-check-ai-favicon.svg` simplifies that artwork for small browser-tab sizes. Preserve original artwork when adding variants.

Maven copies `branding.json` and `assets/` into `static/brand/` in the executable JAR. Docker packages the same catalog; rebuild the application/image after a branding change. The browser loads the packaged JSON from `/brand/branding.json`, and source asset paths map directly to `/brand/assets/`. No remote artwork is fetched and no separate frontend build is required.

The catalog supplies the header/browser title, main tagline, About heading and description, support notice, website/support tooltip languages, project/author links, footer and About copyright, favicon, icon and poster. The copyright year comes from the current browser date. Empty optional links and artwork fields are omitted. The original posters remain unchanged.

The header's **About** button opens a native modal with a dimmed backdrop and 4px blur, a large poster, filled link buttons and a shared support row. The current page uses a footer for the author's copyright/license and website link. Both support destinations are voluntary; the dialog includes the same voluntary-support note as LocalNook and StonePlan.

Speech-bubble tooltips explain destinations and show complete URLs on hover or keyboard focus; the author's website and Ways to support also show the ordered `authorWebsiteLanguages` list (currently hu | en | ro | de). Tooltips stay within the viewport/dialog edges and allow hovering their content. Escape first dismisses the visible tooltip, then closes About. Closing returns focus to About without changing the story, selected model, pending request or completed review.

License identifiers and URLs are not part of the branding contract. The legal source of truth remains the root `LICENSE` and existing SPDX notices; branding edits must not rewrite those or rename stable storage/package identifiers.
