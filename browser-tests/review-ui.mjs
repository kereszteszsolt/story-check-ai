// SPDX-FileCopyrightText: 2026 Keresztes Zsolt <https://kereszteszsolt.hu>
// SPDX-License-Identifier: Apache-2.0
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { mkdir } from 'node:fs/promises';

const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const base = process.env.STC_BASE_URL || 'http://127.0.0.1:33070';
assert(['localhost', '127.0.0.1', '[::1]'].includes(new URL(base).hostname), 'Use a loopback test app.');
const artifacts = process.env.STC_BROWSER_ARTIFACTS || '/tmp/stc004-browser-artifacts';
await mkdir(artifacts, { recursive: true });
const browser = await chromium.launch({ headless: true, executablePath: process.env.STC_CHROMIUM_PATH });
const models = [
    { provider: 'ollama', model: 'fixture/local', label: 'Ollama · fixture/local' },
    { provider: 'openrouter', model: 'fixture/cloud', label: 'OpenRouter · fixture/cloud' }
];
const story = 'As a user, I want to copy literal {code} and Hungarian ő characters unchanged.';
const review = {
    summary: 'The goal is clear. <img src=x onerror="window.injected=true">',
    acceptanceCriteria: ['The original {code} and ő text is copied unchanged.'],
    clarificationQuestions: [],
    suggestedTests: ['Copy text with <script>alert(1)</script> and a newline.']
};
const errors = [];
let passed = 0;

async function run(name, test, { handler, viewport = { width: 1366, height: 1000 }, realApi = false, init } = {}) {
    const context = await browser.newContext({ viewport, permissions: ['clipboard-read', 'clipboard-write'] });
    await context.route('**/*', route => new URL(route.request().url()).origin === new URL(base).origin
        ? route.continue() : route.abort());
    if (init) await context.addInitScript(init);
    const page = await context.newPage();
    page.on('pageerror', error => errors.push(error.message));
    const calls = [];
    if (!realApi) await page.route('**/api/**', async route => {
        const request = route.request();
        calls.push({ path: new URL(request.url()).pathname, method: request.method(), data: request.postDataJSON() });
        if (handler && await handler(route, calls)) return;
        if (request.url().endsWith('/api/models')) await route.fulfill({ json: { models } });
        else if (request.url().endsWith('/api/reviews')) await route.fulfill({ json: review });
        else await route.abort();
    });
    try {
        await page.goto(base);
        await test(page, calls);
        assert.deepEqual(errors, [], 'No uncaught browser errors');
        console.log(`PASS ${++passed}: ${name}`);
    } catch (error) {
        await page.screenshot({ path: `${artifacts}/failure.png`, fullPage: true });
        throw new Error(name, { cause: error });
    } finally { await context.close(); }
}

async function ready(page, selection = '0') {
    await page.locator('#model').selectOption(selection);
    await page.locator('#story').fill(story);
}
async function submit(page) {
    await ready(page);
    await page.getByRole('button', { name: 'Analyze Story', exact: true }).click();
    await page.locator('#review-result').waitFor({ state: 'visible' });
}
async function showsError(page, text) {
    await ready(page);
    await page.locator('#analyze').click();
    await page.locator('#review-error').waitFor({ state: 'visible' });
    assert((await page.locator('#review-error').innerText()).includes(text));
    assert.equal(await page.locator('#analyze').isEnabled(), true);
    assert.equal(await page.locator('#copy').isDisabled(), true);
}

try {
    await run('packaged app: real empty model list and loaded assets', async page => {
        await page.getByText('No models are configured.', { exact: false }).waitFor();
        assert.equal(await page.locator('#model').isDisabled(), true);
        assert.equal(await page.locator('#analyze').isDisabled(), true);
        assert.equal(await page.locator('#retry-models').isVisible(), true);
        assert.equal(await page.locator('h1').innerText(), 'StoryCheck AI');
        await page.screenshot({ path: `${artifacts}/empty-desktop.png`, fullPage: true });
    }, { realApi: true });

    await run('model loading, explicit selection and cloud cost notice', async page => {
        assert.equal(await page.locator('#model').isDisabled(), true);
        assert.equal(await page.locator('#analyze').isDisabled(), true);
        await page.locator('#model').selectOption('1');
        assert.equal(await page.locator('#model option').count(), 3);
        assert((await page.locator('#provider-note').innerText()).includes('may incur charges'));
        assert.equal(await page.locator('#copy').isDisabled(), true);
    }, { handler: async route => {
        if (route.request().url().endsWith('/api/models')) {
            await new Promise(resolve => setTimeout(resolve, 300));
            await route.fulfill({ json: { models } }); return true;
        }
    } });

    await run('model-list failure and explicit reload', async page => {
        await page.locator('#retry-models').waitFor({ state: 'visible' });
        assert.equal(await page.locator('#model').isDisabled(), true);
        assert(!(await page.locator('#model-status').innerText()).includes('PRIVATE'));
        await page.locator('#retry-models').click();
        await ready(page);
        assert.equal(await page.locator('#analyze').isEnabled(), true);
    }, { handler: async (route, calls) => {
        if (calls.length === 1) {
            await route.fulfill({ status: 503, json: { code: 'INTERNAL_ERROR', message: 'PRIVATE key' } }); return true;
        }
    } });

    await run('malformed model choices stay disabled', async page => {
        await page.getByText('The model list is invalid.', { exact: false }).waitFor();
        assert.equal(await page.locator('#analyze').isDisabled(), true);
    }, { handler: async route => { await route.fulfill({ json: { models: [null] } }); return true; } });

    await run('keyboard submission, literal output, real clipboard and stale-result reset', async (page, calls) => {
        await page.locator('#model').selectOption('0');
        await page.locator('#model').focus();
        await page.keyboard.press('Tab');
        assert.equal(await page.locator('#story').evaluate(el => el === document.activeElement), true);
        await page.keyboard.type(story);
        await page.keyboard.press('Tab');
        assert.equal(await page.locator('#analyze').evaluate(el => el === document.activeElement), true);
        await page.keyboard.press('Enter');
        await page.locator('#review-result').waitFor({ state: 'visible' });
        assert.equal(await page.locator('#summary').innerText(), review.summary);
        assert.equal(await page.locator('#review-result img, #review-result script').count(), 0);
        assert.equal(await page.evaluate(() => window.injected), undefined);
        assert.equal(await page.locator('#no-questions').isVisible(), true);
        assert.equal(await page.locator('#review-heading').evaluate(el => el === document.activeElement), true);
        assert.deepEqual(calls.filter(call => call.method === 'POST').map(call => call.data),
            [{ provider: 'ollama', model: 'fixture/local', story }]);
        await page.locator('#copy').click();
        await page.getByText('Markdown copied.', { exact: true }).waitFor();
        const markdown = await page.evaluate(() => navigator.clipboard.readText());
        for (const heading of ['# Story review', '## Summary', '## Acceptance criteria', '## Clarification questions', '## Suggested tests']) assert(markdown.includes(heading));
        assert(markdown.includes('- The original \\{code\\} and ő text'));
        assert(markdown.includes('&lt;img') && !markdown.includes('<img'));
        await page.screenshot({ path: `${artifacts}/review-desktop.png`, fullPage: true });
        await page.locator('#story').fill(story + ' Changed.');
        assert.equal(await page.locator('#review-result').isHidden(), true);
        assert.equal(await page.locator('#copy').isDisabled(), true);
    });

    await run('explicit OpenRouter request and nonempty questions', async (page, calls) => {
        await ready(page, '1'); await page.locator('#analyze').click();
        await page.locator('#review-result').waitFor({ state: 'visible' });
        assert.equal(await page.locator('#clarification-questions li').innerText(), 'Which formats are supported?');
        assert.equal(await page.locator('#no-questions').isHidden(), true);
        assert.equal(calls.filter(call => call.method === 'POST')[0].data.provider, 'openrouter');
    }, { handler: async route => {
        if (route.request().method() === 'POST') {
            await route.fulfill({ json: { ...review, clarificationQuestions: ['Which formats are supported?'] } }); return true;
        }
    } });

    await run('blank/short/oversized input is rejected without a request', async (page, calls) => {
        await ready(page);
        for (const input of [' '.repeat(10), 'short', 'x'.repeat(4001)]) {
            await page.locator('#story').evaluate((el, value) => { el.value = value; }, input);
            await page.locator('#analyze').click();
            assert.equal(await page.locator('#input-error').isVisible(), true);
            assert.equal(await page.locator('#story').getAttribute('aria-invalid'), 'true');
        }
        assert.equal(calls.filter(call => call.method === 'POST').length, 0);
    });

    await run('pending request prevents duplicate submission and selection changes', async (page, calls) => {
        await ready(page); await page.locator('#analyze').click();
        assert.equal(await page.locator('#analyze').isDisabled(), true);
        assert.equal(await page.locator('#model').isDisabled(), true);
        assert.equal(await page.locator('#story').getAttribute('readonly'), '');
        await page.locator('#review-result').waitFor({ state: 'visible' });
        assert.equal(calls.filter(call => call.method === 'POST').length, 1);
    }, { handler: async route => {
        if (route.request().method() === 'POST') {
            await new Promise(resolve => setTimeout(resolve, 400));
            await route.fulfill({ json: review }); return true;
        }
    } });

    await run('clipboard denial exposes selected manual Markdown', async page => {
        await submit(page); await page.locator('#copy').click();
        await page.locator('#copy-fallback').waitFor({ state: 'visible' });
        assert((await page.locator('#copy-status').innerText()).includes('Automatic copy failed'));
        assert((await page.locator('#markdown').inputValue()).startsWith('# Story review'));
        assert.equal(await page.locator('#markdown').evaluate(el => el.selectionEnd === el.value.length), true);
    }, { init: () => Object.defineProperty(navigator, 'clipboard', { value: { writeText: async () => { throw new Error('denied'); } } }) });

    for (const [code, status, message] of [
        ['PROVIDER_TIMEOUT', 504, 'took too long'], ['PROVIDER_RATE_LIMITED', 429, 'rate limited'],
        ['PROVIDER_UNAVAILABLE', 503, 'unavailable'], ['PROVIDER_REJECTED_REQUEST', 502, 'may not support'],
        ['INVALID_PROVIDER_RESPONSE', 502, 'invalid review'], ['MODEL_NOT_AVAILABLE', 422, 'no longer available']
    ]) await run(`safe ${code} and usable retry controls`, page => showsError(page, message), {
        handler: async route => {
            if (route.request().method() === 'POST') {
                await route.fulfill({ status, json: { code, message: '<script>PRIVATE</script>' } }); return true;
            }
        }
    });

    await run('network failure restores controls', page => showsError(page, 'Could not reach'), {
        handler: async route => { if (route.request().method() === 'POST') { await route.abort(); return true; } }
    });
    await run('invalid success body is never displayed or copied', page => showsError(page, 'invalid review'), {
        handler: async route => { if (route.request().method() === 'POST') { await route.fulfill({ json: { summary: '<script>PRIVATE</script>' } }); return true; } }
    });
    await run('client timeout aborts once and restores controls', page => showsError(page, 'timed out'), {
        init: () => {
            const original = window.setTimeout;
            window.setTimeout = (callback, ms, ...args) => original(callback, ms === 150000 ? 50 : ms, ...args);
        },
        handler: async route => { if (route.request().method() === 'POST') { await new Promise(resolve => setTimeout(resolve, 150)); await route.abort(); return true; } }
    });
    for (const width of [320, 390]) await run(`mobile ${width}px: long text and manual copy stay within viewport`, async page => {
        await submit(page); await page.locator('#copy').click();
        await page.locator('#copy-fallback').waitFor({ state: 'visible' });
        assert(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth));
        await page.screenshot({ path: `${artifacts}/review-mobile-${width}.png`, fullPage: true });
    }, {
        viewport: { width, height: 844 },
        init: () => Object.defineProperty(navigator, 'clipboard', { value: undefined }),
        handler: async route => { if (route.request().method() === 'POST') { await route.fulfill({ json: { ...review, summary: 'x'.repeat(600) } }); return true; } }
    });
    await run('About branding, keyboard focus, blur and unchanged draft', async (page, calls) => {
        await ready(page);
        const brand = await (await page.request.get(`${base}/brand/branding.json`)).json();
        const button = page.getByRole('button', { name: 'About', exact: true });
        await button.click();
        const dialog = page.getByRole('dialog');
        await dialog.waitFor();
        assert.equal(await dialog.locator('h2').textContent(), `About ${brand.appName}`);
        assert.equal(await dialog.locator('#about-description').textContent(), brand.appDescription);
        assert.equal(await page.locator('#app-icon').getAttribute('src'), `/brand/${brand.appIcon}`);
        assert.equal(await page.locator('link[rel="icon"]').getAttribute('href'), `/brand/${brand.appFavicon}`);
        await dialog.locator('#about-poster').evaluate(img => img.decode());
        assert.equal(await dialog.locator('#about-poster').getAttribute('src'), `/brand/${brand.appPoster}`);
        assert.equal(await dialog.evaluate(el => getComputedStyle(el, '::backdrop').backdropFilter), 'blur(4px)');
        assert.equal(await page.locator('#close-about').evaluate(el => el === document.activeElement), true);
        await page.keyboard.press('Shift+Tab');
        assert.equal(await dialog.evaluate(el => el.contains(document.activeElement)), true);
        const website = dialog.getByRole('link', { name: new URL(brand.authorWebsite).hostname, exact: true });
        for (const [label, href] of [[new URL(brand.authorWebsite).hostname, brand.authorWebsite],
            ['Source', brand.appGithubUrl], ['GitHub profile', brand.authorGithub],
            ['Buy me a Coffee', brand.authorBuyMeACoffeeUrl], ['Ways to support', brand.authorWaysToSupportUrl]]) {
            const link = dialog.getByRole('link', { name: label, exact: true });
            assert.equal(await link.getAttribute('href'), href);
            assert.equal(await link.getAttribute('rel'), 'noopener noreferrer');
        }
        assert.equal(await dialog.locator('#support-links a').count(), 2);
        assert((await page.locator('#footer-copyright').textContent()).includes(`© ${new Date().getFullYear()} ${brand.authorName}, ${brand.license}`));
        assert.equal(await page.locator('#author-website').getAttribute('href'), brand.authorWebsite);
        assert.equal(await dialog.getByRole('link', { name: 'Project website', exact: true }).count(), 0);
        await website.focus();
        assert((await dialog.getByRole('tooltip').textContent()).includes('hu | en | ro | de.'));
        assert((await dialog.getByRole('tooltip').textContent()).endsWith(brand.authorWebsite));
        await page.keyboard.press('Escape');
        assert.equal(await dialog.getByRole('tooltip').count(), 0);
        assert.equal(await dialog.isVisible(), true);
        await page.screenshot({ path: `${artifacts}/about-desktop.png`, fullPage: false });
        await page.keyboard.press('Escape');
        await dialog.waitFor({ state: 'hidden' });
        assert.equal(await button.evaluate(el => el === document.activeElement), true);
        assert.equal(await page.locator('#story').inputValue(), story);
        assert.equal(await page.locator('#model').inputValue(), '0');
        assert.equal(calls.filter(call => call.method === 'POST').length, 0);
    });

    await run('About preserves the completed review and copy controls', async page => {
        await submit(page);
        await page.locator('#about-button').click();
        await page.locator('#close-about').click();
        assert.equal(await page.locator('#summary').textContent(), review.summary);
        assert.equal(await page.locator('#copy').isEnabled(), true);
        assert.equal(await page.locator('#story').inputValue(), story);
    });

    await run('hover and focus replace one tooltip and allow hovering its content', async page => {
        await page.locator('#about-button').click();
        const dialog = page.getByRole('dialog');
        const website = dialog.locator('#project-links a').first();
        await website.hover();
        const tip = dialog.getByRole('tooltip');
        assert((await tip.textContent()).includes("Author's website"));
        const support = dialog.getByRole('link', { name: 'Ways to support', exact: true });
        await support.focus();
        assert.equal(await tip.count(), 1);
        assert((await tip.textContent()).includes('Ways to support the author. Available in hu | en | ro | de.'));
        assert.equal(await website.getAttribute('aria-describedby'), null);
        await tip.hover();
        await page.waitForTimeout(160);
        assert.equal(await tip.isVisible(), true);
        await page.keyboard.press('Escape');
        assert.equal(await tip.count(), 0);
        await page.locator('#close-about').click();
    });

    await run('a pending review completes without moving focus outside About', async (page, calls) => {
        await ready(page);
        await page.locator('#analyze').click();
        await page.locator('#about-button').click();
        await page.locator('#review-result').waitFor({ state: 'visible' });
        assert.equal(await page.getByRole('dialog').evaluate(el => el.contains(document.activeElement)), true);
        await page.locator('#close-about').click();
        assert.equal(await page.locator('#summary').textContent(), review.summary);
        assert.equal(calls.filter(call => call.method === 'POST').length, 1);
    }, { handler: async route => {
        if (route.request().method() === 'POST') {
            await new Promise(resolve => setTimeout(resolve, 700));
            await route.fulfill({ json: review }); return true;
        }
    } });

    await run('branding failure leaves the review flow available', async page => {
        await submit(page);
        assert.equal(await page.locator('#about-button').isDisabled(), true);
        assert.equal(await page.locator('#summary').textContent(), review.summary);
        assert.equal(await page.locator('#copy').isEnabled(), true);
    }, { init: () => {
        const originalFetch = window.fetch;
        window.fetch = (input, ...args) => input === '/brand/branding.json'
            ? Promise.reject(new TypeError('Branding fixture unavailable')) : originalFetch(input, ...args);
    } });

    for (const width of [320, 390]) await run(`About ${width}px: poster, links and tooltip stay inside the viewport`, async page => {
        await page.locator('#about-button').click();
        const dialog = page.getByRole('dialog');
        assert.equal(await dialog.evaluate(el => el.scrollWidth <= el.clientWidth), true);
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
        await dialog.locator('#about-poster').evaluate(img => img.decode());
        await page.screenshot({ path: `${artifacts}/about-mobile-${width}.png`, fullPage: false });
        await dialog.getByRole('link', { name: 'Ways to support', exact: true }).focus();
        const tip = await dialog.getByRole('tooltip').boundingBox();
        assert(tip.x >= 0 && tip.x + tip.width <= width);
        assert(tip.y >= 0 && tip.y + tip.height <= 844);
        await page.keyboard.press('Escape');
        await page.keyboard.press('Escape');
        await dialog.waitFor({ state: 'hidden' });
    }, { viewport: { width, height: 844 } });

    console.log(`${passed} browser scenarios passed. Provider responses were fixed browser fixtures; no live model requests.`);
} finally { await browser.close(); }
