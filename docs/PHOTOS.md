# Sliding Tiles personal photos

Pro campaign boards offer **Choose photo** / **Change photo**. Android's
`PickVisualMedia(ImageOnly)` contract opens the native picker where available and
the system document fallback on older devices. No library-wide storage/media
permission is declared. A returned content URI is read once; source filenames,
URIs and persistent URI grants are not saved.

The player confirms a square center crop. Cancel preserves the previous artwork
and board. EXIF orientation, including mirrors, is applied before cropping;
transparency becomes white and animations use their first frame. The copy becomes
sRGB JPEG without metadata. Selection/conversion/preview pause active play time;
copy/decode work runs off the main thread. Preview state survives Activity recreation
in the board ViewModel, but an unconfirmed preview is discarded after process death.
A restored picker callback waits for and checks the original attempt ID.

## Persistence and access

`Attempt.photoId` is presentation metadata. It does not change the level, engine
state, tile identity, scoring, move/history records or hint eligibility. A tile's
solved identity selects its crop at every board size and zoom. Number labels,
accessibility descriptions/actions and the complete-picture preview remain available.
Daily challenges always use bundled original artwork and never offer personal photos.

Room schema 4 adds private `photos` rows. SHA-256 identifies normalized JPEGs;
identical byte copies share a row. Image and reference commit in one transaction.
No source permission is needed for reopening, moving, undoing or restarting a
saved board. Restart carries the reference forward; a new campaign board begins
with bundled artwork until the player selects a photo.

Pro is checked before and at the end of a photo transaction. Pro loss retains
images/progress and renders numbers until ownership returns. The default artwork
action and privacy deletion remain available without Pro. Missing/corrupt local
images produce an error and a usable numbered board, preserving progress.

## Limits and deletion

- Input: 32 MiB, at most 16,384 on either edge and 64 million source pixels.
  Unsupported/unreadable images leave the current board intact.
- Decoded intermediate: at most 2,048 on the longer edge, with power-of-two sampling
  on API 26. Final square: at most 1,024; small inputs are not upscaled. These are
  image-allocation bounds, not a whole-process heap claim.
- Stored JPEG: 512 KiB. Quality tries 90, 82, 74, 66, 58 and 50 until it fits.
- Current unique photo payloads: 8 MiB. Replacing/resetting a board prunes images
  without attempt references. Completed history may still own a copy; the previous
  import snapshot carries its own bounded payloads.
- **Settings → Saved photos → Delete all saved photos** removes references/copies
  from current attempts, history and local recovery after confirmation. Moves,
  scores and reward safeguards remain. Deletion is serialized with backup work
  and continues in application scope after leaving Settings; it also discards
  private staged imports. Originals and already exported files
  are not modified.

Source bytes are temporarily copied into private cache and deleted in `finally`.
Startup removes interrupted copies and discards unconfirmed staged backup previews.
Those locations are excluded from backup. No photo bytes, URIs or filenames are
sent to Billing, ads or crash reporting. Future crash-reporting integration must
preserve this boundary; that integration remains an open release item.

## Backups

Manual schema 4 includes referenced images as bounded Base64 chunks, preserving
the photo contract introduced in schema 3. Schema 3 remains accepted with photos;
schema 2 without them. Schema 1 and future schemas remain rejected.
Validation requires exactly one payload per referenced image, matching SHA-256,
canonical Base64, bounded aggregate size, baseline JPEG without EXIF/comments,
square bounded dimensions and successful decode. Only campaign Tiles may reference
photos. All validation precedes replacement.

Images, references, previous progress and the pending-settings marker commit in
one Room transaction. Invalid/missing images or SQL failure leave the old board
and recovery intact. Recovery restores previous images without granting Pro.
The preview shows the photo count. The whole file remains limited to 32 MiB;
see [BACKUPS.md](BACKUPS.md).

Automatic backup/device transfer includes database image rows and recovery.
Android's 25 MB cloud quota is separate: a large history/database/recovery copy
can exceed it. Settings advises a manual backup for large collections. Emulator
checks do not prove successful automatic transfer or physical heap qualification.

## Qualification

Local checks cover orientation, crop/normalization/limits, source deletion, disk
reopen, move/undo/restart, migrations, legacy files, export/import/recovery,
invalid payload rejection, SQL rollback, deduplication, privacy deletion, Pro loss
and Daily isolation. UI checks cover crop identity at 3×3/4×4/5×5, native selection,
the API 26 document fallback, and 320×640 dp / 200% text dialogs.
The recorded evidence links the final reports,
capture hashes, minified Settings smoke and exact qualification scope.

Arbitrary cloud providers, real permission revocation/process-kill timing, physical
heap/performance, human TalkBack, wide-gamut/camera format coverage and automatic
Android restore remain qualification work. The full v1 gates remain open.

Primary references: [photo picker/fallback](https://developer.android.com/training/data-storage/shared/photo-picker),
[ImageDecoder](https://developer.android.com/reference/android/graphics/ImageDecoder),
[ExifInterface](https://developer.android.com/reference/androidx/exifinterface/media/ExifInterface),
[ExifInterface 1.4.2](https://developer.android.com/jetpack/androidx/releases/exifinterface),
and [Auto Backup quota](https://developer.android.com/identity/data/autobackup).
