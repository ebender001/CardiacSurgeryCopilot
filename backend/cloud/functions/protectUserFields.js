/**
 * Server-controlled fields on `_User`. Parse lets a signed-in user update
 * their own `_User` row with just the public client key, so any field the
 * server trusts must be locked here -- otherwise a user could grant
 * themselves `isReviewer` and bypass the subscription paywall.
 *
 * `isReviewer` marks the app-store review account (Google Play / App Store
 * reviewers cannot purchase a subscription). It is set only from the
 * Back4App dashboard or other master-key code, both of which pass the
 * `request.master` check below.
 */
const PROTECTED_USER_FIELDS = ['isReviewer'];

Parse.Cloud.beforeSave(Parse.User, (request) => {
  if (request.master) return;

  for (const field of PROTECTED_USER_FIELDS) {
    if (request.object.dirty(field)) {
      throw new Parse.Error(
        Parse.Error.OPERATION_FORBIDDEN,
        'This field can only be changed by the server.'
      );
    }
  }
});
