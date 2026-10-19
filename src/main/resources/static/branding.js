// SPDX-FileCopyrightText: 2026 Keresztes Zsolt <https://kereszteszsolt.hu>
// SPDX-License-Identifier: Apache-2.0
const about = document.getElementById('about-dialog');
const aboutButton = document.getElementById('about-button');
const licenseUrl = 'https://www.apache.org/licenses/LICENSE-2.0';
const icons = {
    globe: '<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c5 5 5 13 0 18-5-5-5-13 0-18Z"/>',
    code: '<path d="m8 6-6 6 6 6m8-12 6 6-6 6m-2-15-4 18"/>',
    profile: '<circle cx="12" cy="7" r="4"/><path d="M4 21v-2a8 8 0 0 1 16 0v2Z"/>',
    heart: '<path d="m12 21-8-8a5.5 5.5 0 0 1 8-7.5A5.5 5.5 0 0 1 20 13Z"/>',
    coffee: '<path d="M4 8h13v9a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4Zm13 1h2a3 3 0 0 1 0 6h-2M7 2v3m4-3v3m4-3v3"/>'
};
let activeTooltip;
let hideTimer;
let nextTooltip = 0;

function hideTooltip() {
    clearTimeout(hideTimer);
    if (!activeTooltip) return;
    const { anchor, bubble, previousDescription } = activeTooltip;
    bubble.remove();
    if (previousDescription) anchor.setAttribute('aria-describedby', previousDescription);
    else anchor.removeAttribute('aria-describedby');
    activeTooltip = null;
}

function scheduleHide() {
    clearTimeout(hideTimer);
    hideTimer = setTimeout(() => {
        if (activeTooltip && !activeTooltip.anchor.matches(':hover, :focus')
            && !activeTooltip.bubble.matches(':hover')) hideTooltip();
    }, 120);
}

function showTooltip(anchor, description, preferredWidth, languages) {
    clearTimeout(hideTimer);
    if (activeTooltip?.anchor === anchor) return;
    hideTooltip();
    const bubble = document.createElement('div');
    bubble.id = `link-tooltip-${++nextTooltip}`;
    bubble.className = 'link-tooltip';
    bubble.setAttribute('role', 'tooltip');
    bubble.textContent = description;
    const languageIndex = languages ? description.indexOf(languages) : -1;
    if (languageIndex !== -1) {
        const languageList = document.createElement('span');
        languageList.className = 'tooltip-languages';
        languageList.textContent = languages;
        bubble.replaceChildren(document.createTextNode(description.slice(0, languageIndex)), languageList,
            document.createTextNode(description.slice(languageIndex + languages.length)));
    }
    // Modal tooltips must share the dialog's top layer.
    const dialog = anchor.closest('dialog');
    (dialog || document.body).append(bubble);
    const previousDescription = anchor.getAttribute('aria-describedby');
    anchor.setAttribute('aria-describedby', [previousDescription, bubble.id].filter(Boolean).join(' '));
    activeTooltip = { anchor, bubble, previousDescription };
    bubble.addEventListener('mouseenter', () => clearTimeout(hideTimer));
    bubble.addEventListener('mouseleave', scheduleHide);
    const bounds = dialog?.getBoundingClientRect();
    const leftEdge = Math.max(10, (bounds?.left ?? 0) + 10);
    const rightEdge = Math.min(innerWidth - 10, (bounds?.right ?? innerWidth) - 10);
    const topEdge = Math.max(10, (bounds?.top ?? 0) + 10);
    const bottomEdge = Math.min(innerHeight - 10, (bounds?.bottom ?? innerHeight) - 10);
    bubble.style.maxWidth = `${Math.min(preferredWidth, rightEdge - leftEdge)}px`;
    const target = anchor.getBoundingClientRect();
    const size = bubble.getBoundingClientRect();
    const center = target.left + target.width / 2;
    const left = Math.max(leftEdge, Math.min(center - size.width / 2, rightEdge - size.width));
    const above = target.top - size.height - 12 >= topEdge;
    bubble.classList.toggle('below', !above);
    bubble.style.left = `${left}px`;
    bubble.style.top = `${Math.max(topEdge, Math.min(above ? target.top - size.height - 12
        : target.bottom + 12, bottomEdge - size.height))}px`;
    bubble.style.setProperty('--tooltip-arrow', `${Math.max(12, Math.min(center - left, size.width - 12))}px`);
}

function tooltip(anchor, description, preferredWidth = 320, languages = '') {
    anchor.addEventListener('mouseenter', () => showTooltip(anchor, description, preferredWidth, languages));
    anchor.addEventListener('focus', () => showTooltip(anchor, description, preferredWidth, languages));
    anchor.addEventListener('mouseleave', scheduleHide);
    anchor.addEventListener('blur', scheduleHide);
}

document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && activeTooltip) {
        event.preventDefault();
        event.stopPropagation();
        hideTooltip();
    }
}, true);
document.addEventListener('scroll', hideTooltip, true);
window.addEventListener('resize', hideTooltip);
aboutButton.addEventListener('click', () => {
    hideTooltip();
    about.showModal();
    document.body.classList.add('about-open');
});
document.getElementById('close-about').addEventListener('click', () => about.close());
about.addEventListener('keydown', event => {
    if (event.key !== 'Tab') return;
    const controls = [...about.querySelectorAll('button:not(:disabled), a[href]')];
    const first = controls[0];
    const last = controls.at(-1);
    if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
    }
});
about.addEventListener('close', () => {
    hideTooltip();
    document.body.classList.remove('about-open');
    aboutButton.focus({ preventScroll: true });
});
about.addEventListener('click', event => {
    if (event.target !== about) return;
    const bounds = about.getBoundingClientRect();
    if (event.clientX < bounds.left || event.clientX > bounds.right
        || event.clientY < bounds.top || event.clientY > bounds.bottom) about.close();
});

function assetUrl(path) {
    if (typeof path !== 'string' || !/^assets\/[a-zA-Z0-9_./-]+$/.test(path)
        || path.split('/').some(part => part === '..' || part === '.')) return '';
    return `/brand/${path}`;
}

function linkUrl(value) {
    try {
        return ['https:', 'http:'].includes(new URL(value).protocol) ? value : '';
    } catch { return ''; }
}

function externalLink(label, href, description, tooltipWidth = 320, languages = '') {
    const link = document.createElement('a');
    link.textContent = label;
    link.href = href;
    link.target = '_blank';
    link.rel = 'noopener noreferrer';
    tooltip(link, `${description}\n${href}`, tooltipWidth, languages);
    return link;
}

function copyright(container, brand) {
    const link = externalLink('Apache-2.0', licenseUrl, 'Read the Apache-2.0 license.');
    link.className = 'license-link';
    container.replaceChildren(document.createTextNode(`© ${new Date().getFullYear()} ${brand.authorName}, `), link);
    container.hidden = false;
}

async function loadBrand() {
    const response = await fetch('/brand/branding.json', { signal: AbortSignal.timeout(10000) });
    if (!response.ok) throw new Error('Branding unavailable');
    const brand = await response.json();
    if (['appName', 'authorName', 'appDescription', 'appTagline', 'supportRemarkText']
        .some(key => typeof brand[key] !== 'string' || !brand[key].trim())
        || !Array.isArray(brand.authorWebsiteLanguages)
        || brand.authorWebsiteLanguages.some(language => typeof language !== 'string' || !language.trim())) {
        throw new Error('Invalid branding');
    }
    document.title = brand.appName;
    document.querySelector('.brand h1').textContent = brand.appName;
    document.querySelector('meta[name="description"]').content = brand.appDescription;
    document.getElementById('about-heading').textContent = `About ${brand.appName}`;
    document.getElementById('about-description').textContent = brand.appDescription;
    document.getElementById('app-tagline').textContent = brand.appTagline;
    const supportRemark = document.getElementById('support-remark');
    const sentenceEnd = brand.supportRemarkText.indexOf('. ');
    supportRemark.textContent = brand.supportRemarkText;
    if (sentenceEnd !== -1) {
        supportRemark.replaceChildren(document.createTextNode(brand.supportRemarkText.slice(0, sentenceEnd + 1)),
            document.createElement('br'), document.createTextNode(brand.supportRemarkText.slice(sentenceEnd + 2)));
    }
    const languages = brand.authorWebsiteLanguages.map(language => language.trim()).join(' | ');
    const languageList = languages ? `${languages}.` : '';
    const languageNote = languages ? ` Available in ${languageList}` : '';
    const authorDescription = `Author's website.${languageNote}`;
    for (const [id, field] of [['app-icon', 'appIcon'], ['about-poster', 'appPoster']]) {
        const image = document.getElementById(id);
        const url = assetUrl(brand[field]);
        image.hidden = !url;
        if (url) image.src = url;
    }
    document.getElementById('about-poster').alt = `${brand.appName} project poster`;
    const favicon = document.querySelector('link[rel="icon"]');
    const faviconUrl = assetUrl(brand.appFavicon);
    if (faviconUrl) favicon.href = faviconUrl;
    else favicon.remove();
    const authorWebsite = linkUrl(brand.authorWebsite);
    const authorDomain = authorWebsite ? new URL(authorWebsite).hostname : '';
    const links = [
        ['website', 'globe', authorDomain, authorWebsite, authorDescription],
        ['source', 'code', 'Source', brand.appGithubUrl, `View the ${brand.appName} source code on GitHub.`],
        ['profile', 'profile', 'GitHub profile', brand.authorGithub, "The author's GitHub profile."],
        ['project', 'globe', 'Project website', brand.appWebsite, `Visit the ${brand.appName} website.`],
        ['coffee', 'coffee', 'Buy me a Coffee', brand.authorBuyMeACoffeeUrl, 'Support the author with a coffee on Buy Me a Coffee.'],
        ['support', 'heart', 'Ways to support', brand.authorWaysToSupportUrl, `Ways to support the author.${languageNote}`]
    ];
    for (const [id, icon, label, href, description] of links) {
        if (!linkUrl(href)) continue;
        const link = externalLink(label, href, description, id === 'support' ? 368 : 320,
            ['website', 'support'].includes(id) ? languageList : '');
        link.className = `about-action${id === 'coffee' ? ' coffee-action' : ''}`;
        const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        svg.setAttribute('viewBox', '0 0 24 24');
        svg.setAttribute('aria-hidden', 'true');
        // Only the fixed, handwritten icon paths above are inserted as markup.
        svg.innerHTML = icons[icon];
        link.prepend(svg);
        document.getElementById(['coffee', 'support'].includes(id) ? 'support-links' : 'project-links').append(link);
    }
    copyright(document.getElementById('about-copyright'), brand);
    copyright(document.getElementById('footer-copyright'), brand);
    if (authorWebsite) {
        const website = document.getElementById('author-website');
        website.textContent = authorDomain;
        website.href = authorWebsite;
        website.hidden = false;
        tooltip(website, `${authorDescription}\n${authorWebsite}`, 320, languageList);
    }
    aboutButton.disabled = false;
}

loadBrand().catch(() => {
    aboutButton.title = 'About is unavailable. Reload the page to try again.';
});
