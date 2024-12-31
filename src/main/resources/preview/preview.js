const MAX_LINES_PER_PAGE = 15;
const MAX_CHARS_PER_LINE = 25;
const MAX_CHARS_PER_PAGE = MAX_LINES_PER_PAGE * MAX_CHARS_PER_LINE;

class BookFormatter {
    constructor() {
        this.pages = [];
    }

    formatText(text) {
        const lines = text.split('\n');
        let currentPage = [];
        let currentPageChars = 0;
        let currentPageLines = 0;

        // Always start with first page
        if (lines.length > 0) {
            this.pages = [];
        }

        for (let line of lines) {
            // Check for page break marker (case insensitive)
            if (line.trim().toLowerCase() === '[page]') {
                if (currentPage.length > 0) {
                    this.pages.push(currentPage.join('\n'));
                }
                currentPage = [];
                currentPageChars = 0;
                currentPageLines = 0;
                continue;
            }

            const formattedLine = this.formatLine(line);
            const lineLength = this.getContentLength(formattedLine);

            // Check if adding this line would exceed page limits
            if (currentPageLines >= MAX_LINES_PER_PAGE || 
                currentPageChars + lineLength > MAX_CHARS_PER_PAGE) {
                if (currentPage.length > 0) {
                    this.pages.push(currentPage.join('\n'));
                }
                currentPage = [];
                currentPageChars = 0;
                currentPageLines = 0;
            }

            // Add line to current page
            currentPage.push(formattedLine);
            currentPageChars += lineLength;
            currentPageLines++;
        }

        // Add the last page if it has content
        if (currentPage.length > 0) {
            this.pages.push(currentPage.join('\n'));
        }

        // If no pages were created, create an empty first page
        if (this.pages.length === 0) {
            this.pages.push('');
        }

        return this.pages;
    }

    getContentLength(line) {
        // Remove HTML tags to get actual content length
        return line.replace(/<[^>]*>/g, '').length;
    }

    formatLine(line) {
        let formattedLine = line;

        // Handle lists (only if dash is followed by space)
        if (line.match(/^- /)) {
            formattedLine = '・' + line.substring(2);
        }
        // Don't convert dashes that are part of coordinates or other content
        else if (line.match(/^-\d/)) {
            formattedLine = line;
        }

        // Handle bold (**text**)
        formattedLine = formattedLine.replace(/\*\*(.*?)\*\*/g, '<span class="bold">$1</span>');
        
        // Handle italic (*text*)
        formattedLine = formattedLine.replace(/\*(.*?)\*/g, '<span class="italic">$1</span>');
        
        // Handle underline (_text_)
        formattedLine = formattedLine.replace(/_(.*?)_/g, '<span class="underline">$1</span>');

        // Handle colors (<c>text)
        formattedLine = formattedLine.replace(/<([0-9a-f])>(.*?)(?:<>|$)/g, '<span class="color-$1">$2</span>');

        return formattedLine;
    }
}

class BookPreview {
    constructor() {
        this.formatter = new BookFormatter();
        this.currentPage = 1;
        this.pages = [];

        // DOM elements
        this.markdownInput = document.getElementById('markdownInput');
        this.bookPages = document.getElementById('bookPages');
        this.prevButton = document.getElementById('prevPage');
        this.nextButton = document.getElementById('nextPage');
        this.pageNumber = document.getElementById('pageNumber');

        this.initializeEventListeners();
    }

    initializeEventListeners() {
        // Use input event for immediate updates
        this.markdownInput.addEventListener('input', () => {
            requestAnimationFrame(() => this.updatePreview());
        });
        
        // Also handle paste events separately
        this.markdownInput.addEventListener('paste', () => {
            setTimeout(() => this.updatePreview(), 0);
        });
        
        this.prevButton.addEventListener('click', () => this.previousPage());
        this.nextButton.addEventListener('click', () => this.nextPage());
        
        // Initial preview
        this.updatePreview();
    }

    updatePreview() {
        const markdown = this.markdownInput.value;
        this.pages = this.formatter.formatText(markdown);
        
        // Keep current page if possible, otherwise go to first page
        if (this.currentPage > this.pages.length) {
            this.currentPage = 1;
        }
        
        this.renderCurrentPage();
        this.updateNavigationButtons();
    }

    renderCurrentPage() {
        const pageContent = this.pages[this.currentPage - 1] || '';
        this.bookPages.innerHTML = pageContent;
        this.pageNumber.textContent = `Page ${this.currentPage} of ${this.pages.length}`;
    }

    updateNavigationButtons() {
        this.prevButton.disabled = this.currentPage <= 1;
        this.nextButton.disabled = this.currentPage >= this.pages.length;
    }

    previousPage() {
        if (this.currentPage > 1) {
            this.currentPage--;
            this.renderCurrentPage();
            this.updateNavigationButtons();
        }
    }

    nextPage() {
        if (this.currentPage < this.pages.length) {
            this.currentPage++;
            this.renderCurrentPage();
            this.updateNavigationButtons();
        }
    }
}

// Initialize the preview when the page loads
document.addEventListener('DOMContentLoaded', () => {
    new BookPreview();
});
