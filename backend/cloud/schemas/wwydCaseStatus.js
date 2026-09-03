/**
 * Centralized CSCWwydCase status values. Nothing else in the codebase
 * should reference raw status strings -- import WwydCaseStatus instead.
 */
const WwydCaseStatus = Object.freeze({
  ACTIVE: 'active',
  ARCHIVED: 'archived',
});

const ALL_STATUSES = Object.freeze(Object.values(WwydCaseStatus));

function isValidStatus(status) {
  return ALL_STATUSES.includes(status);
}

module.exports = { WwydCaseStatus, ALL_STATUSES, isValidStatus };
