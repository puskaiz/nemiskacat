import { describe, expect, it } from "vitest";
import { i18n } from "./index";

describe("i18n", () => {
  it("defaults to Hungarian", () => {
    expect(i18n.language).toBe("hu");
    expect(i18n.t("nav:orders")).toBe("Rendelések");
  });
  it("switches to English", async () => {
    await i18n.changeLanguage("en");
    expect(i18n.t("nav:orders")).toBe("Orders");
    await i18n.changeLanguage("hu");
  });

  it("resolves Refine's notification keys (no raw key leaks)", async () => {
    // Refine fires these under the default (common) namespace; a missing/colliding
    // key would surface the raw "notifications.deleteSuccess" string in the UI.
    expect(i18n.t("notifications.deleteSuccess")).toBe("Sikeresen törölve");
    expect(i18n.t("notifications.success")).toBe("Siker");
    // The profile-menu label was renamed to free up the `notifications` object.
    expect(i18n.t("notificationsMenu")).toBe("Értesítések");
    await i18n.changeLanguage("en");
    expect(i18n.t("notifications.deleteSuccess")).toBe("Successfully deleted");
    await i18n.changeLanguage("hu");
  });
});
