// SPDX-FileCopyrightText: 2026 Keresztes Zsolt <https://kereszteszsolt.hu>
// SPDX-License-Identifier: Apache-2.0
const $ = (id) => document.getElementById(id);
const form = $('review-form');
const model = $('model');
const story = $('story');
let models = [];
let busy = false;
let currentReview = null;

const errors = {
    INVALID_REQUEST: 'Check the selected model and enter a story between 10 and 4,000 characters.',
    PAYLOAD_TOO_LARGE: 'The story request is too large. Please shorten it.',
    MODEL_NOT_AVAILABLE: 'This model is no longer available. Reload the model list and choose again.',
    PROVIDER_TIMEOUT: 'The selected model took too long. You can try again when it is ready.',
    PROVIDER_RATE_LIMITED: 'The selected provider is rate limited. Wait before trying again.',
    PROVIDER_UNAVAILABLE: 'The selected provider is unavailable. Check its server configuration or try again later.',
    PROVIDER_REJECTED_REQUEST: 'The selected model rejected this review request. It may not support structured output.',
    INVALID_PROVIDER_RESPONSE: 'The selected model returned an invalid review. You can try again or choose another model.'
};

function selectedModel() { return model.value === '' ? null : models[Number(model.value)]; }
function syncControls() {
    model.disabled = busy || models.length === 0;
    story.readOnly = busy;
    $('analyze').disabled = busy || !selectedModel();
    $('analyze').textContent = busy ? 'Reviewing…' : 'Analyze Story';
    $('retry-models').disabled = busy;
    $('copy').disabled = busy || !currentReview;
    $('review-panel').setAttribute('aria-busy', String(busy));
}

async function requestJson(url, options = {}, timeout = 10000) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeout);
    try {
        const response = await fetch(url, { ...options, signal: controller.signal, headers: {
            Accept: 'application/json', ...options.headers
        }});
        const data = await response.json();
        if (!response.ok) throw new Error(errors[data?.code] || 'The request could not be completed. Please try again.');
        return data;
    } catch (error) {
        if (controller.signal.aborted) throw new Error('The request timed out. Please try again when the server is ready.');
        if (error instanceof TypeError || error instanceof SyntaxError) throw new Error('Could not reach the app or read its response. Check the connection and try again.');
        throw error;
    } finally {
        clearTimeout(timer);
    }
}

async function loadModels() {
    models = [];
    model.replaceChildren(new Option('Loading models…', ''));
    $('model-status').textContent = 'Loading available models…';
    $('retry-models').hidden = true;
    $('provider-note').hidden = true;
    clearReview();
    syncControls();
    try {
        const data = await requestJson('/api/models');
        if (!Array.isArray(data?.models) || data.models.length > 6 || data.models.some(choice =>
            !choice || !['ollama', 'openrouter'].includes(choice.provider) || typeof choice.model !== 'string'
            || !choice.model || typeof choice.label !== 'string' || !choice.label)) throw new Error('The model list is invalid. Please reload it.');
        models = data.models;
        model.replaceChildren(new Option(models.length ? 'Choose a model…' : 'No models available', ''));
        for (const provider of ['ollama', 'openrouter']) {
            const group = document.createElement('optgroup');
            group.label = provider === 'ollama' ? 'Ollama' : 'OpenRouter';
            models.forEach((choice, index) => {
                if (choice.provider === provider) group.append(new Option(choice.label, String(index)));
            });
            if (group.children.length) model.append(group);
        }
        $('model-status').textContent = models.length ? 'Choose where to send your story.'
            : 'No models are configured. Configure an installed Ollama model or an OpenRouter model and key on the server, then reload.';
        $('retry-models').hidden = models.length > 0;
    } catch (error) {
        model.replaceChildren(new Option('Models unavailable', ''));
        $('model-status').textContent = error.message;
        $('retry-models').hidden = false;
    } finally {
        syncControls();
    }
}

function clearReview() {
    currentReview = null;
    $('review-result').hidden = true;
    $('review-empty').hidden = false;
    $('copy-fallback').hidden = true;
    $('markdown').value = '';
    $('copy-status').textContent = '';
    $('request-status').textContent = '';
    $('review-error').hidden = true;
    syncControls();
}

function validReview(review) {
    const text = (value, max) => typeof value === 'string' && value.trim().length > 0 && value.length <= max;
    const list = (value, min, max, length) => Array.isArray(value) && value.length >= min && value.length <= max
        && value.every(item => text(item, length));
    return review && Object.keys(review).length === 4 && text(review.summary, 600)
        && list(review.acceptanceCriteria, 1, 8, 500) && list(review.clarificationQuestions, 0, 6, 300)
        && list(review.suggestedTests, 1, 8, 500);
}

function renderReview(review, choice) {
    $('summary').textContent = review.summary;
    for (const [field, id] of [['acceptanceCriteria', 'acceptance-criteria'],
        ['clarificationQuestions', 'clarification-questions'], ['suggestedTests', 'suggested-tests']]) {
        const items = review[field].map(text => {
            const item = document.createElement('li');
            item.textContent = text;
            return item;
        });
        $(id).replaceChildren(...items);
    }
    $('no-questions').hidden = review.clarificationQuestions.length > 0;
    $('review-source').textContent = choice.label;
    $('review-empty').hidden = true;
    $('review-result').hidden = false;
    currentReview = review;
}

// Escape model text so pasted Markdown retains literal content and cannot create HTML.
function markdownText(text) {
    return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/([\\`*_{}\[\]()#+.!|~-])/g, '\\$1').replace(/\r\n?|\n/g, '\n  ');
}

function reviewMarkdown(review) {
    const sections = [['Acceptance criteria', review.acceptanceCriteria],
        ['Clarification questions', review.clarificationQuestions], ['Suggested tests', review.suggestedTests]];
    return ['# Story review', '', '## Summary', '', markdownText(review.summary), '',
        ...sections.flatMap(([heading, items]) => ['## ' + heading, '',
            ...(items.length ? items.map(item => '- ' + markdownText(item)) : ['No clarification questions suggested.']), ''])].join('\n');
}

form.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (busy) return;
    const choice = selectedModel();
    const invalid = !story.value.trim() || story.value.length < 10 || story.value.length > 4000;
    $('input-error').hidden = !invalid;
    $('input-error').textContent = invalid ? 'Enter a story between 10 and 4,000 characters.' : '';
    story.setAttribute('aria-invalid', String(invalid));
    if (invalid) { story.focus(); return; }
    if (!choice) { model.focus(); return; }
    clearReview();
    busy = true;
    syncControls();
    $('request-status').textContent = 'Reviewing your story… This may take up to two minutes.';
    try {
        const review = await requestJson('/api/reviews', { method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ provider: choice.provider, model: choice.model, story: story.value }) }, 150000);
        if (!validReview(review)) throw new Error(errors.INVALID_PROVIDER_RESPONSE);
        renderReview(review, choice);
        $('request-status').textContent = 'Review ready. Check the suggestions before using them.';
        $('review-heading').focus();
    } catch (error) {
        $('request-status').textContent = '';
        $('review-error').textContent = error.message;
        $('review-error').hidden = false;
        if (error.message === errors.MODEL_NOT_AVAILABLE) $('retry-models').hidden = false;
    } finally {
        busy = false;
        syncControls();
    }
});

story.addEventListener('input', () => {
    $('story-count').textContent = story.value.length.toLocaleString('en-US') + ' / 4,000';
    story.removeAttribute('aria-invalid');
    $('input-error').hidden = true;
    clearReview();
});
model.addEventListener('change', () => {
    clearReview();
    const choice = selectedModel();
    $('provider-note').hidden = !choice;
    $('provider-note').textContent = choice?.provider === 'openrouter'
        ? 'Your story will be sent to OpenRouter. Usage may incur charges.' : 'Your story will be sent to the selected Ollama server.';
});
$('retry-models').addEventListener('click', loadModels);
$('copy').addEventListener('click', async () => {
    if (!currentReview) return;
    const copiedReview = currentReview;
    const markdown = reviewMarkdown(copiedReview);
    $('copy').disabled = true;
    try {
        await navigator.clipboard.writeText(markdown);
        if (currentReview !== copiedReview) return;
        $('copy-fallback').hidden = true;
        $('copy-status').textContent = 'Markdown copied.';
    } catch {
        if (currentReview !== copiedReview) return;
        $('copy-status').textContent = 'Automatic copy failed. Markdown is ready to copy manually below.';
        $('markdown').value = markdown;
        $('copy-fallback').hidden = false;
        $('markdown').focus();
        $('markdown').select();
    } finally {
        syncControls();
    }
});
loadModels();
