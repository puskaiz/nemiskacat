export type BlockType = "AUTHOR" | "CATEGORIES" | "CTA" | "CONTACT" | "SOCIAL" | "INSTAGRAM";

export interface AuthorContent { name: string; bio: string; photoUrl: string; goals?: string; }
export interface CtaContent { title: string; buttonLabel: string; url: string; imageUrl: string; description?: string; }
export interface ContactContent { title: string; phone: string; email: string; address: string; openingHours?: string; }
export interface SocialLink { network: string; url: string; }
export interface SocialContent { title: string; links: SocialLink[]; }
export interface CategoriesContent { title: string; }
// Posts are loaded live from the Instagram cache; only the heading and how many to show are stored.
export interface InstagramContent { title: string; count?: number; }

export const BLOCK_LABELS: Record<BlockType, string> = {
  AUTHOR: "Szerző",
  CATEGORIES: "Kategóriák",
  CTA: "Ajánló (CTA)",
  CONTACT: "Elérhetőség",
  SOCIAL: "Közösségi linkek",
  INSTAGRAM: "Instagram",
};

export function parseContent(_blockType: BlockType, json: string): Record<string, unknown> {
  if (!json) return {};
  try {
    const v = JSON.parse(json);
    return v !== null && typeof v === "object" && !Array.isArray(v) ? (v as Record<string, unknown>) : {};
  } catch {
    return {};
  }
}

export function serializeContent(_blockType: BlockType, value: Record<string, unknown>): string {
  return JSON.stringify(value ?? {});
}
