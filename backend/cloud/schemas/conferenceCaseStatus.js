/**
 * Centralized CSCConferenceCase status values. Nothing else in the
 * codebase should reference raw status strings -- import
 * ConferenceCaseStatus instead.
 */
const ConferenceCaseStatus = Object.freeze({
  COLLECTING_INFORMATION: 'collecting_information',
  READY_TO_FINALIZE: 'ready_to_finalize',
  COMPLETED: 'completed',
});

const ALL_STATUSES = Object.freeze(Object.values(ConferenceCaseStatus));

function isValidStatus(status) {
  return ALL_STATUSES.includes(status);
}

module.exports = { ConferenceCaseStatus, ALL_STATUSES, isValidStatus };
