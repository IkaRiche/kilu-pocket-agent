package com.kilu.pocketagent.core.hub.web

object WebExtractScripts {

    /**
     * Extracts paragraphs up to 5000 chars total.
     * Strips whitespace and basic formatting.
     */
    val EXTRACT_PARAGRAPHS = """
        (function() {
            let text = "";
            let ps = document.querySelectorAll('p');
            for (let p of ps) {
                let inner = p.innerText || p.textContent || "";
                inner = inner.replace(/\s+/g, ' ').trim();
                // ignore scripts/styles just in case although p shouldn't have them usually
                if (inner.length > 0 && !p.querySelector('script') && !p.querySelector('style')) {
                    text += inner + "\n\n";
                }
                if (text.length > 5000) break;
            }
            return text.substring(0, 5000).trim();
        })();
    """.trimIndent()

    /**
     * Extracts headings (h1, h2, h3) up to 20 items.
     * Each item is clamped to 200 chars.
     * Returns a JSON string of a string array.
     */
    val EXTRACT_HEADINGS = """
        (function() {
            let result = [];
            let hs = document.querySelectorAll('h1, h2, h3');
            for (let h of hs) {
                let inner = h.innerText || h.textContent || "";
                inner = inner.replace(/\s+/g, ' ').trim();
                if (inner.length > 0) {
                    result.push(inner.substring(0, 200));
                }
                if (result.length >= 20) break;
            }
            return JSON.stringify(result);
        })();
    """.trimIndent()

    /**
     * Evaluates security heuristics locally in DOM.
     * Returns a non-empty string giving the reason if a trigger is hit.
     * Returns empty string if safe.
     */
    val CHECK_HEURISTICS = """
        (function() {
            if (document.querySelector('input[type="password"]')) {
                return "Found password input field";
            }
            
            // Check for captcha in visible text (simplified)
            let bodyText = (document.body.innerText || "").toLowerCase();
            if (bodyText.includes("captcha")) {
                return "Found captcha keyword in page text";
            }
            
            let dangerousKeywords = ["paywall", "subscribe", "login", "auth"];
            // We check body classes/ids or prominent structural nodes to not over-trigger on normal text.
            let elements = document.querySelectorAll('body, header, nav, div[id], div[class]');
            for (let el of elements) {
                let id = (el.id || "").toLowerCase();
                let cls = (el.className || "").toString().toLowerCase();
                
                for (let d of dangerousKeywords) {
                    if (id.includes(d) || cls.includes(d)) {
                        return "Matched dangerous class/id: " + d;
                    }
                }
            }
            
            return "";
        })();
    """.trimIndent()
}
