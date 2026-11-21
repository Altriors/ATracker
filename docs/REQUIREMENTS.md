# Project: Personal Media Library – Android

## 1. Project Overview

Build a polished, feature-rich, single-user Android application that acts as a private personal entertainment library and tracking platform.

The application should combine the functionality of MyAnimeList, AniList and manga/light novel tracking platforms, but it is exclusively for personal use.

It must support:

1. Anime
2. Manga
3. Manhwa
4. Light Novels
5. Web Novels

The primary objective is to maintain a comprehensive online-discoverable catalogue while storing all personal user activity, progress, ratings and library information locally on the device.

This must be an **offline-first application**, not a web application wrapped inside an Android WebView.

Do not build a basic CRUD application. The goal is a polished, maintainable, long-term personal media management system.

---

## 2. Core Architecture

Follow an offline-first architecture.

### Online functionality
The application should retrieve information from available online metadata sources and APIs.

Information to retrieve includes:

- Title and alternative titles
- Cover images
- Synopsis
- Genres and tags
- Authors and artists
- Studios and publishers
- Release dates
- Publication status
- Episode, chapter and volume information when available
- Adaptation relationships
- Ratings from external sources, when available

### Local functionality

Use a local SQLite database as the primary source of truth for personal data.

Store locally:

- Personal library entries
- Watching and reading status
- Episode and chapter progress
- Volume progress
- Personal ratings
- Personal reviews and notes
- Start and completion dates
- Last activity dates
- Custom lists
- Tags and favourites
- Activity history
- Personal statistics

The application must remain usable without an internet connection.

### Critical data ownership rule

External metadata updates must NEVER overwrite personal information.

For example:

If I have read 500 chapters of a novel and the online catalogue gets refreshed, my progress must remain at 500 chapters.

Online catalogue updates and personal tracking data must be independent.

Provide manual refresh and optional automatic catalogue refresh.

Do not require an account, login or cloud subscription.

---

## 3. Media Categories

Every category must have its own independent tracking system.

### A. Anime

Support:

- TV series
- Movies
- OVAs
- ONAs
- Specials
- Short anime

Statuses:

- Watching
- Completed
- On Hold
- Dropped
- Plan to Watch
- Not Started

Track:

- Episodes watched
- Total episodes
- Start date
- Completion date
- Last watched date
- Personal rating
- Review
- Rewatch count

### B. Manga

Track:

- Chapters read
- Total chapters
- Volumes read
- Total volumes
- Start date
- Completion date
- Last reading date
- Rating
- Review

### C. Manhwa

Support Korean manhwa and webtoons.

Track:

- Chapters read
- Total chapters
- Publication status
- Reading status
- Start and completion dates
- Personal rating
- Reading history

### D. Light Novels

Track:

- Volumes read
- Total volumes
- Optional chapter progress
- Publication status
- Start and completion dates
- Personal ratings
- Reading history

### E. Web Novels

Support novels such as Reverend Insanity, Lord of the Mysteries and other web novels.

Track:

- Chapters read
- Total chapters when known
- Publication status
- Last read chapter
- Start date
- Completion date
- Reading history
- Personal rating
- Personal notes

Do not assume that every novel has a fixed chapter count. Support unknown totals and continuously updated chapter counts.

---

## 4. Linked Adaptations and Franchises

This is one of the most important features.

Different adaptations of the same story must have separate database entries but should be connected through a franchise or story relationship.

Example:

Reverend Insanity:
- Web Novel
- Manga (if an entry exists)
- Anime (if an entry exists)

Lord of the Mysteries:
- Web Novel
- Manhua
- Anime

Each adaptation must have:

- Its own progress
- Its own status
- Its own rating
- Its own start/completion dates
- Its own history
- Its own statistics

However, all related entries should appear together on a franchise page.

### Franchise page

Display:

- Original source material
- Related adaptations
- Available formats
- Individual progress
- Individual ratings
- Combined franchise statistics

Allow manual linking and unlinking of entries.

Do not automatically merge entries just because their names are similar.

### Important statistics rule

Maintain separate statistics for each format.

For example:

- Total anime episodes watched
- Total manga chapters read
- Total manhwa chapters read
- Total novel volumes read
- Total web novel chapters read

Also provide combined franchise activity statistics.

Never add chapters and episodes together as if they were the same unit.

Do not double-count progress when calculating statistics.

---

## 5. Global Catalogue and Discovery

The application should have an online-discoverable catalogue containing previously released and currently existing media.

Support:

- Completed titles
- Ongoing titles
- Upcoming titles
- Hiatus titles
- Cancelled titles
- Older titles
- Lesser-known titles
- New catalogue entries

I do not need an extensive anime announcement/news system, but it would be useful to have an optional new releases section.

### Catalogue behaviour

When searching for a title that I have never watched or read:

1. Search the local catalogue first.
2. If unavailable, search supported online metadata sources.
3. Retrieve its available metadata.
4. Display the title page.
5. Allow me to add it to my personal library.
6. Store the relevant metadata locally.
7. Track all future progress locally.

The global catalogue must not force every discovered title into my personal library.

Only add an item to my personal collection when I explicitly choose to do so.

### Metadata providers

Evaluate suitable sources such as:

- AniList API
- MyAnimeList API
- Jikan API
- MangaDex API
- Open Library
- Google Books API
- Other legitimate metadata sources for light novels and web novels

Do not assume one provider covers every category.

Build a provider abstraction layer so additional sources can be integrated later.

Respect API rate limits, attribution requirements and terms of service.

Do not scrape websites aggressively or bypass authentication and access restrictions.

When multiple sources provide information, prioritize reliable metadata and retain source attribution.

Handle unavailable metadata gracefully.

Never fabricate publication dates, chapter counts, authors or completion statuses.

---

## 6. Personal Library

Create a dedicated library section.

Allow filtering by:

- Media type
- Watching/reading status
- Genre
- Release year
- Publication status
- Rating
- Alphabetical order
- Recently updated
- Recently added

Allow sorting and searching across the entire personal collection.

Support:

- Favourites
- Custom lists
- Personal tags
- Custom notes
- Rewatch and reread tracking

The library must be independent of the global catalogue.

---

## 7. Activity History

Maintain a detailed local activity log.

Record events such as:

- Started watching/reading
- Episode completed
- Chapter completed
- Volume completed
- Progress updated
- Title completed
- Title dropped
- Status changed
- Rating changed

Every activity should have a timestamp.

Allow viewing activity by:

- Day
- Week
- Month
- Year

Allow corrections to activity history without corrupting current progress.

---

## 8. Statistics Dashboard

Build a dedicated statistics dashboard.

### Anime statistics
- Total anime watched
- Completed anime
- Episodes watched
- Estimated watching time
- Movies watched
- Favourite genres
- Average rating

### Manga and manhwa statistics
- Total manga completed
- Total manhwa completed
- Total chapters read
- Total volumes read
- Reading activity over time

### Light novel and web novel statistics
- Total novels read
- Total volumes read
- Total chapters read
- Reading activity over time

### Overall statistics
- Total titles in personal library
- Total completed titles
- Currently watching/reading
- On hold
- Dropped
- Plan to watch/read
- Recently active titles
- Favourite franchises
- Favourite authors
- Favourite studios
- Favourite genres

Provide visual charts and historical progress graphs.

Keep units separate. An anime episode must never be treated as equivalent to a manga chapter.

Statistics must be calculated from actual local tracking data.

---

## 9. Calendar and Release Tracking

Provide an optional calendar section.

Show available upcoming information such as:

- Upcoming anime episodes
- Anime release dates
- Upcoming manga releases
- Upcoming manhwa chapters
- Upcoming novel volumes

Only show dates supported by reliable metadata.

Allow disabling this feature entirely.

---

## 10. Application UI/UX

The UI should be modern, polished and designed specifically for Android.

Avoid generic CRUD screens and basic default component styling.

Design inspiration:

- AniList
- MyAnimeList
- Modern media library applications
- Premium reading applications

### Navigation

Use a bottom navigation bar:

1. Home
2. Discover
3. My Library
4. Statistics
5. Settings

### Home screen

Display:

- Continue Watching
- Continue Reading
- Recently Updated
- Recently Added
- Favourite Titles
- Quick Progress Update

### Discover screen

Provide:

- Search bar
- Category filters
- Genre filters
- Popular titles
- Recently released titles
- Advanced search

### Title detail page

Display:

- Cover image
- Title and alternative titles
- Synopsis
- Genres
- Author/studio/publisher
- Release information
- Publication status
- Total episodes/chapters/volumes when known
- Personal tracking status
- Personal rating
- Progress controls
- Related adaptations
- Personal notes

Make progress updates quick and convenient.

### Library screen

Provide both grid and list layouts.

Use attractive cover cards and proper empty states.

### Settings

Include:

- Theme selection
- Local database management
- Backup and restore
- Metadata provider configuration
- Catalogue refresh settings
- Activity history settings
- Data export
- Storage information

Support dark mode and light mode.

---

## 11. Database Design

Design a normalized local database.

Consider tables/entities such as:

- Media
- MediaMetadata
- MediaType
- Franchise
- FranchiseRelationship
- UserLibrary
- UserProgress
- UserRating
- UserReview
- UserNotes
- ActivityHistory
- CustomLists
- CustomListItems
- UserTags
- MediaGenres
- MetadataSources
- CatalogueCache
- ReleaseInformation

Separate globally sourced media information from user-specific information.

A single media entity should be capable of being associated with multiple metadata providers.

Use stable internal identifiers and external provider identifiers.

Support migrations and database versioning.

Avoid storing personal progress directly inside global metadata records.

---

## 12. Backup and Restore

Although the primary database is local, data safety is essential.

Provide:

- Manual full backup
- Restore from backup
- JSON export
- CSV export where appropriate
- Database backup
- Import personal lists from MyAnimeList when possible

Backups should include all personal tracking information, ratings, reviews, lists and activity history.

Make it clear that uninstalling the application can delete local data unless it has been backed up.

---

## 13. Technical Requirements

Choose an appropriate modern Android technology stack.

Preferred options:

- Kotlin
- Jetpack Compose
- Room Database (SQLite)
- Kotlin Coroutines and Flow
- Retrofit or Ktor for network communication
- WorkManager for scheduled catalogue synchronization
- Coil for image loading

Use clean architecture with clearly separated:

- Presentation layer
- Domain layer
- Data layer

Implement repository patterns and provider abstractions.

The app must be responsive, efficient and maintainable.

Do not overengineer the application, but ensure the architecture supports future expansion.

---

## 14. Reliability and Edge Cases

Handle:

- Missing chapter counts
- Unknown publication status
- Multiple alternative titles
- Duplicate titles
- Different adaptations with similar names
- Interrupted network requests
- Offline operation
- API rate limits
- Invalid metadata
- Image loading failures
- Database migrations
- Partial catalogue updates
- Duplicate activity events

Do not delete local progress when a title disappears from an online metadata provider.

Do not assume that all media have a conventional ending or fixed release schedule.

---

## 15. Development Workflow

Before implementation:

1. Analyze the requirements.
2. Propose the complete architecture.
3. Design the database schema.
4. Identify appropriate metadata providers for all five categories.
5. Evaluate API coverage and limitations.
6. Design the application navigation and UI.
7. Prepare an implementation roadmap.

Then implement in phases.

Suggested phases:

Phase 1: Project foundation, architecture and database.

Phase 2: Media catalogue and metadata provider integrations.

Phase 3: Personal library and progress tracking.

Phase 4: Linked adaptations and franchise management.

Phase 5: Activity history and statistics.

Phase 6: Search, discovery and filtering.

Phase 7: Calendar and release tracking.

Phase 8: Backup, restore and import/export.

Phase 9: UI polish, performance and testing.

Do not implement everything in one enormous untested change.

Build incrementally, test each phase and maintain working application builds.

---

## 16. Final Product Expectations

The finished application should feel like a premium personal media library.

It should allow me to:

- Discover virtually any anime, manga, manhwa, light novel or web novel.
- Add titles to my personal collection.
- Track exactly where I stopped watching or reading.
- Maintain independent progress for different adaptations.
- Connect related adaptations into franchises.
- See combined and individual statistics.
- View my historical activity.
- Use my library without an internet connection.
- Keep my data private and stored locally.
- Back up and restore my entire collection.

The most important principles are:

1. Offline-first.
2. Local ownership of personal data.
3. Reliable and expandable metadata catalogue.
4. Separate tracking for every adaptation.
5. Linked franchise relationships.
6. Accurate statistics.
7. Excellent Android user experience.

**Important instruction:** Do not sacrifice architectural correctness or data reliability for visual polish. First establish a sound foundation, then build the complete application around it.

Before writing code, present the architecture, API/provider feasibility analysis, database schema and phased development plan. Clearly identify any catalogue coverage limitations instead of claiming that every published title is guaranteed to exist.