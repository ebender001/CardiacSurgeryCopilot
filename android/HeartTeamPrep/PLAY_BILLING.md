# Play Billing setup

The app sells two auto-renewing subscriptions, mirroring the iOS app. Create
them in Play Console > Monetize > Products > Subscriptions (the app must have
an uploaded build on at least an internal-testing track first):

| Product ID              | Base plan       | Price  | Offer                  |
|-------------------------|-----------------|--------|------------------------|
| `heartteamprep_monthly` | monthly, renews | $1.99  | free trial, 3 days     |
| `heartteamprep_annual`  | yearly, renews | $9.99  | free trial, 3 days     |

* Product IDs live in `SubscriptionProductId` (`service/SubscriptionService.kt`).
* The paywall uses the offer that has a free-trial phase if one exists, and
  reads trial length and price from Play -- nothing is hard-coded.
* Test with a license-tester account (Play Console > Settings > License
  testing) on a build installed from an internal-testing track.
  Sideloaded debug builds can't load products ("Unable to Load Subscriptions").
* Entitlement is checked client-side against Play on every "Start a New Case".
  Purchases are acknowledged by the client. There is no server-side receipt
  verification, matching the iOS app.
