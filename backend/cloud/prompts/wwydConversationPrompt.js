const { WWYD_ATTENDING_PERSONA } = require('./persona');

const WWYD_CONVERSATION_PROMPT_VERSION = '1.0.0';

/**
 * Builds the prompt for one turn of the live "What Would You Do"
 * discussion. Sends the full conversation history every turn (same
 * "send full history, no server-side summarization" approach used
 * elsewhere in this backend) so the model's replies stay coherent with
 * everything already said, not just the trainee's latest message.
 */
function buildWwydConversationPrompt({ casePresentation, startingQuestion, conversation, newMessage }) {
  const historyBlock = conversation
    .map((entry) => `${entry.role === 'assistant' ? 'You' : 'Trainee'}: ${entry.text}`)
    .join('\n\n');

  const system = `${WWYD_ATTENDING_PERSONA}

Continue this "What Would You Do" discussion. Ask one focused follow-up at a time, push the trainee to justify their reasoning rather than just accepting an answer, and introduce a complicating factor or alternative viewpoint when it would deepen the discussion. Stay grounded in what the trainee has actually told you about the case -- never invent clinical facts that weren't stated or clearly implied. Keep each reply conversational and concise (a few sentences), the way a real attending would talk, not a written report.

Respond only with a JSON object of this exact shape, no other text:
{ "reply": "<your next reply in the conversation>" }`;

  const user = `Case presentation:
"""
${casePresentation}
"""

Your opening question:
"""
${startingQuestion}
"""

Conversation so far:
${historyBlock || '(nothing yet -- the trainee is about to respond to your opening question)'}

Trainee's latest message:
"""
${newMessage}
"""`;

  return { system, user };
}

module.exports = { WWYD_CONVERSATION_PROMPT_VERSION, buildWwydConversationPrompt };
