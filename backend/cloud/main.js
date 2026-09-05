/**
 * Cloud Code entry point. This file only registers Cloud Functions --
 * business logic lives in services/, ai/, repositories/, etc. See README.md
 * for the full architecture overview.
 */

// Preoperative Case Conference workflow
require('./functions/createConferenceCase');
require('./functions/answerConferenceQuestion');
require('./functions/skipRemainingConferenceQuestions');
require('./functions/finalizeConferenceCase');
require('./functions/getConferenceCase');
require('./functions/listConferenceCases');
require('./functions/updateConferenceReport');
require('./functions/findConferenceReferences');
require('./functions/getConferenceHeartTeamResponses');
require('./functions/getHeartTeamRoleEvidence');

// Shared, cross-workflow
require('./functions/correctDictation');
require('./functions/deleteAccount');
