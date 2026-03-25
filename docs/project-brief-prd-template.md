# Project brief / PRD — context checklist & template

You've asked for a project PRD or brief, but you haven't provided any context yet!

To create a useful document, I need information about the project. Please provide details on:

1. **What is the product/feature you want to build or improve?** (e.g., a new mobile app, a feature for an existing web application, a backend system, an improvement to a process)
2. **What problem are you trying to solve?** (Why is this project important? What pain point does it address for users or the business?)
3. **Who is the target audience?** (Who will use this product/feature?)
4. **What are the main goals or objectives for this project?** (What do you hope to achieve?)
5. **What existing product or system does this relate to (if any)?**
6. **Are there any known constraints or considerations?** (e.g., budget, timeline, technical limitations, specific compliance requirements)
7. **Do you have any initial ideas for features or functionality?**

---

**In the meantime, I can provide a template and a hypothetical example based on a common product scenario. This will show you the structure and type of information I'd fill in once you provide your context.**

---

## Hypothetical example: Project brief — "Dark Mode" feature for a web application

---

### 1. Project overview / executive summary

- **Project name:** Dark Mode Feature Implementation
- **Version:** 1.0
- **Date:** October 26, 2023
- **Author:** [Your Name/Product Manager]

This document outlines the scope and requirements for implementing a "Dark Mode" feature within our existing web application, "InsightPro Dashboard." The goal is to provide users with an alternative visual theme that reduces eye strain in low-light environments, conserves battery life on OLED screens, and offers a personalized user experience. This feature aims to improve user satisfaction and align with modern UI/UX trends.

### 2. Problem statement

Many of our users, particularly those who work extended hours, in low-light conditions, or at night, report eye strain and fatigue when using the InsightPro Dashboard's default light theme. Additionally, there's a growing industry trend and user expectation for applications to offer a Dark Mode option, which can enhance accessibility and perceived modernity. Users currently lack the ability to customize their visual interface to suit their personal preferences or working environments.

### 3. Goals & objectives

- **Improve user satisfaction:** Increase overall user satisfaction by providing a highly requested feature (measure with NPS and feature adoption rate).
- **Reduce eye strain:** Mitigate user complaints related to eye fatigue during prolonged use (monitor support tickets/feedback related to eye strain).
- **Enhance accessibility:** Offer a more comfortable viewing experience for users with light sensitivity or who prefer high contrast (qualitative feedback).
- **Modernize user experience:** Align InsightPro with current UI/UX best practices and user expectations for personalization.
- **Increase engagement:** Potentially increase session duration or repeat visits from users who prefer dark themes.

### 4. Target audience

- **Primary:** Existing InsightPro Dashboard users who work in diverse lighting conditions, especially those frequently using the application for extended periods.
- **Secondary:** New users attracted by modern features and accessibility options.

### 5. Scope (in & out)

**In scope:**

- Implementation of a "Dark Mode" toggle in user settings.
- Application of the dark theme across all core dashboard pages (e.g., Home, Analytics, Reports, Settings).
- Persistence of the user's chosen theme preference across sessions and devices.
- Basic consideration for accessibility contrast standards for both light and dark modes.
- Integration with system-level dark mode preferences (e.g., macOS, Windows, iOS, Android browser settings).

**Out of scope (for this phase):**

- Customizable themes beyond light and dark.
- Dark mode for any embedded third-party widgets or integrations (unless explicitly supported by the third party).
- Dark mode for administrative panels or internal tools (focus is on customer-facing UI).
- Dynamic theme switching based on time of day.

### 6. Key features & functionality

- **Theme toggle:** A clear and accessible toggle switch in the "User Settings" section.
- **System preference detection:** Automatically apply Dark Mode if the user's operating system/browser is set to prefer dark themes on their first visit (with an option to override).
- **Persistent setting:** The application will remember the user's last chosen theme preference (light or dark) on subsequent visits.
- **Visual consistency:** All primary UI elements (text, backgrounds, buttons, charts, navigation) must be adapted for optimal readability and aesthetic coherence in Dark Mode.
- **Accessibility:** Ensure sufficient contrast ratios for text and UI elements in Dark Mode to meet WCAG guidelines (AA minimum).

### 7. User stories (examples)

- **As a user,** I want to be able to **toggle between Light Mode and Dark Mode** so that I can choose the visual theme I prefer.
- **As a user,** I want the application to **automatically apply Dark Mode if my operating system prefers it** so that my theme preference is consistent across my devices.
- **As a user,** I want the application to **remember my chosen theme** so that I don't have to change it every time I visit.
- **As a user,** I want to be able to **easily read all content and navigate the application in Dark Mode** so that I can use it comfortably.

### 8. Technical considerations

- **CSS variables/theming system:** Leverage CSS variables for easy theme switching and maintainability.
- **Performance:** Ensure theme switching is instantaneous and does not negatively impact page load times or rendering performance.
- **Browser compatibility:** Support all currently supported browsers for InsightPro Dashboard.
- **Existing UI component library:** Audit and update existing UI components to support both themes.
- **Asset management:** Ensure all icons, illustrations, and images are optimized or adapted for both themes.

### 9. Dependencies

- **Design team:** Requires UI/UX design for the Dark Mode color palette, component styling, and icon adjustments.
- **Engineering team:** Requires frontend and potentially backend (for preference persistence) development resources.
- **QA team:** Requires thorough testing across browsers, devices, and accessibility standards.
- **Product marketing:** Communication plan for feature launch.

### 10. Success metrics

- **Adoption rate:** Percentage of active users who enable Dark Mode within 30 days of launch.
- **User feedback:** Positive sentiment in surveys, app store reviews, and reduced support tickets related to eye strain or UI preferences.
- **NPS score:** Monitor changes in Net Promoter Score post-launch.
- **Session duration:** Monitor if there's any impact on average user session duration.

### 11. High-level timeline (estimate)

- **Phase 1: Discovery & design (2–3 weeks)**
  - Detailed UI/UX design for Dark Mode.
  - Technical spike/feasibility assessment.
- **Phase 2: Development (4–6 weeks)**
  - Frontend development of the theming system.
  - Integration into core components.
  - Backend development for preference persistence.
- **Phase 3: QA & bug fixing (2 weeks)**
  - Comprehensive testing across devices and browsers.
  - Accessibility testing.
- **Phase 4: Launch (1 week)**
  - Staged rollout or full release.

### 12. Open questions & risks

- **Color palette consistency:** Ensuring a harmonious and accessible color palette for Dark Mode across all components without looking "inverted."
- **Third-party embeds:** How to handle embedded third-party content (e.g., YouTube videos, external charts) that may not support Dark Mode.
- **Performance impact:** Will the theming system introduce any noticeable performance overhead? (Mitigation: thorough testing)
- **Existing component migration:** How much effort will it be to adapt every existing UI component to Dark Mode? (Mitigation: Phased approach, focus on critical paths first)

---

**Now, please provide your project context, and I'll create a tailored PRD or brief for you!**
