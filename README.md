# Expense Tracker

A personal expense tracker that captures spending **automatically** by reading bank/UPI SMS
and app notifications on-device, categorizing each transaction, and notifying you instantly —
no manual entry, no data leaving the phone.

## Why native Android (not a PWA)

The spec allowed for a PWA fallback if native felt too heavy, but the core requirement —
*read incoming SMS and other apps' notifications in the background, with no user action* — has
no browser equivalent. There is no Web API that lets a PWA read the device's SMS inbox or listen
to other apps' notifications; that's OS-level access gated behind Android's `RECEIVE_SMS`/
`READ_SMS` permissions and `NotificationListenerService`, both unavailable to web content for
very good security reasons. A PWA could only get this data via manual entry or by asking the user
to forward messages — which is exactly what this project is trying to avoid. So this is a native
Android app: **Kotlin + Jetpack Compose**, chosen over Flutter/React Native because SMS and
notification-listener access are Android-specific anyway — a cross-platform framework buys
nothing here and adds a bridge layer between the capture code and the OS APIs it depends on.

## Architecture

Two Gradle modules:

- **`core/`** — plain Kotlin, zero Android dependencies. SMS/notification-body parsers, the
  categorization engine, and their models. Because it has no Android dependency, it's unit
  tested with plain JUnit and runs (and passes, see below) without an Android SDK or emulator —
  useful for CI and for anyone extending bank coverage without booting an emulator.
- **`app/`** — the Android app: Room database, the SMS `BroadcastReceiver`, the
  `NotificationListenerService`, notifications, budgets/alerts, and the Compose UI.

```
core/…/parser/          TransactionParser interface + one implementation per bank/app format,
                         tried in order by ParserRegistry, ending in a generic fallback that
                         flags anything unrecognized as "needs review" instead of dropping it.
core/…/categorize/       CategoryEngine: learned merchant overrides > editable keyword table
                         > UNCATEGORIZED (never a silent guess).
app/…/capture/            SmsReceiver + NotificationCaptureService, both funneling into
                         TransactionCaptureProcessor (parse -> categorize -> save -> notify ->
                         check budgets) so SMS and UPI-app-notification transactions are
                         handled identically.
app/…/data/db/            Room: TransactionEntity, AccountEntity, MerchantRuleEntity,
                         KeywordRuleEntity, BudgetEntity.
app/…/notify/             Instant + large-transaction notifications.
app/…/worker/             Budget 80%/100% alerts, WorkManager weekly summary.
app/…/export/             CSV export.
app/…/ui/                 Compose: Dashboard, Transactions (+ review queue), Settings
                         (rules/budgets/thresholds/backup toggle/export).
```

## What's implemented vs. what's next

Built incrementally per the brief — automatic capture + categorization + notification first,
then coverage/dashboard/budgets:

**Working now:**
- SMS parsers for **SBI, HDFC Bank, ICICI, Axis, Kotak**, a **UPI-app notification** parser
  (GPay/PhonePe/Paytm/BHIM-style "You paid/received ₹X to/from Y"), and a **generic fallback**
  parser for the common "Rs./INR debited/credited" shape from any bank not yet covered by name.
  OTPs and promotional messages are explicitly excluded so they never become fake transactions.
- Keyword-based auto-categorization into all 11 requested sectors, with per-merchant learning
  (correct it once, remembered forever) that always takes priority over the keyword table.
- Instant notification on every capture, a higher-priority variant + confirm-category deep link
  for transactions over your threshold, and messages that couldn't be parsed/categorized
  confidently are surfaced as "needs review" rather than silently guessed.
- Room-backed dashboard (balance per account, this-month sector pie chart, month-over-month
  jump flags, recurring-payment detection), per-sector budgets with 80%/100% alerts, a weekly
  summary via WorkManager, CSV export by month/sector, and a Settings screen to edit keyword
  rules and thresholds.
- **24 passing JUnit tests** in `core/` covering every bank parser, the UPI/generic/OTP/promo
  paths, and the categorization engine (see "Verifying it" below).

**Deliberately deferred / good next steps:**
- More bank parsers (the six covered are the common formats; adding a seventh is one new class
  in `core/…/parser/banks/` plus a registry entry — see "Adding a bank" below).
- The "encrypted backup" toggle in Settings is wired up but only gates the CSV export path today;
  a one-tap full encrypted export/restore (via `androidx.security` `EncryptedFile`) is the
  natural next step and was scoped out to keep this first pass reviewable.
- A dedicated onboarding flow (the permissions banner covers the essentials but a first-run
  wizard would explain *why* each permission is needed before asking).
- Instrumented/UI tests for the Compose screens (the `app/` module's correctness currently rests
  on the `core/` unit tests plus careful manual review — see the note below on why `app/` itself
  isn't build-verified in this environment).

## Adding a bank

1. Create `core/src/main/kotlin/com/expensetracker/core/parser/banks/YourBankParser.kt`
   implementing `TransactionParser` (copy `SbiParser.kt` as a template).
2. Add it to `ParserRegistry.defaultParsers()` **before** `GenericFallbackParser` (order matters —
   first match wins).
3. Add a test in `core/src/test/kotlin/.../BankParserTest.kt` with a real (redacted) sample
   message and run `./gradlew :core:test`.

## Privacy

- SMS and notification bodies are parsed **entirely on-device**; nothing is ever sent over the
  network — there is no backend and no analytics SDK in this project.
- No net-banking login or bank credentials are ever requested. Only read access to SMS/
  notifications, which are already delivered to the phone.
- A message that doesn't match any parser is logged as "needs review" locally, never dropped
  silently and never uploaded anywhere for "better parsing" — you can always inspect the raw
  text before it's turned into a transaction.
- Auto-backup is explicitly excluded (`backup_rules.xml` / `data_extraction_rules.xml`) so
  Android's stock cloud backup doesn't silently upload your transaction history.
- `NotificationListenerService` is a system-wide permission — while granted, Android delivers
  every notification's metadata to `NotificationCaptureService`. It immediately discards
  anything not from a known UPI app package, and discards (never logs) any body that doesn't
  parse as a transaction. This tradeoff is documented in code
  (`capture/notification/NotificationCaptureService.kt`) and is the only way to catch UPI app
  payments that never send an SMS at all.

## Setup

Requires Android Studio (Koala+) with an SDK for API 34 installed — this repo does not vendor
the Android SDK.

```
git clone <this repo>
cd sai-expenses
```

Open in Android Studio and let it sync, or from the CLI once `ANDROID_HOME` is set:

```
./gradlew :app:assembleDebug
```

On first launch, grant the two permission prompts (SMS, notifications) and follow the link to
enable **Notification Access** for UPI-app capture (Android requires this be granted from system
Settings, not a normal in-app dialog — the app links you straight there).

## Verifying it

The `core/` module — parsers and categorization, the actual logic this app depends on for
correctness — is plain Kotlin and runs without an Android SDK or emulator:

```
./gradlew :core:test
```

This currently passes 24/24 tests (16 parser tests across all 6 bank/app formats plus the OTP/
promo-exclusion and unparsed-review cases, 8 categorization tests including the merchant-learning
and keyword-editing behavior).

**Note on `app/`:** this was built in a sandboxed environment without the Android SDK or network
access to Google's Maven repository, so the Android module itself (Room/Compose/manifest wiring)
could not be compiled or run here — there's no emulator or physical device involved in the above
test run. It follows standard, well-documented patterns throughout (Room entities/DAOs,
`NotificationListenerService`, WorkManager periodic work, Compose Navigation), and every file was
reviewed by hand for consistency, but you should do a `./gradlew :app:assembleDebug` and a real
on-device smoke test (send yourself a test bank-format SMS, or use `adb shell service call
isms 5 ...` / the emulator's SMS simulation) before trusting it with real transactions.
