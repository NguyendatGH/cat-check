import type { ReactNode } from "react";


const HEADING_PATTERN = /^(#{1,3})\s+(.*)$/;

function headingId(index: number): string {
  return `legal-section-${String(index)}`;
}

export interface MarkdownHeading {
  id: string;
  label: string;
  level: number;
}

export function extractMarkdownHeadings(source: string): MarkdownHeading[] {
  const headings: MarkdownHeading[] = [];
  for (const line of source.replace(/\r\n/g, "\n").split("\n")) {
    const match = HEADING_PATTERN.exec(line);
    if (!match) continue;
    headings.push({
      id: headingId(headings.length),
      label: match[2].replace(/\*\*/g, "").trim(),
      level: match[1].length,
    });
  }
  return headings;
}

export function stripLeadingTitle(source: string, title: string): string {
  const lines = source.replace(/\r\n/g, "\n").split("\n");
  const first = lines.findIndex((line) => line.trim() !== "");
  if (first === -1) return source;
  const match = /^#\s+(.*)$/.exec(lines[first] ?? "");
  if (!match || match[1].replace(/\*\*/g, "").trim() !== title.trim()) return source;
  return lines.slice(first + 1).join("\n");
}

function renderInline(text: string, keyPrefix: string): ReactNode[] {
  const parts = text.split(/(\*\*[^*]+\*\*)/g);
  return parts.map((part, index) => {
    if (part.startsWith("**") && part.endsWith("**") && part.length > 4) {
      return <strong key={`${keyPrefix}-${String(index)}`}>{part.slice(2, -2)}</strong>;
    }
    return <span key={`${keyPrefix}-${String(index)}`}>{part}</span>;
  });
}

export function renderMarkdown(source: string): ReactNode {
  const lines = source.replace(/\r\n/g, "\n").split("\n");
  const blocks: ReactNode[] = [];
  let i = 0;
  let blockKey = 0;
  let headingIndex = 0;

  while (i < lines.length) {
    const line = lines[i] ?? "";

    if (line.trim() === "") {
      i += 1;
      continue;
    }

    const headingMatch = HEADING_PATTERN.exec(line);
    if (headingMatch) {
      const level = headingMatch[1].length;
      const text = headingMatch[2];
      const key = `h-${String(blockKey++)}`;
      const anchorId = headingId(headingIndex++);
      if (level === 1) {
        blocks.push(
          <h2 key={key} id={anchorId} className="scroll-mt-24 text-h2 font-bold text-text-primary">
            {renderInline(text, key)}
          </h2>,
        );
      } else if (level === 2) {
        blocks.push(
          <h3 key={key} id={anchorId} className="scroll-mt-24 text-h3 font-bold text-text-primary">
            {renderInline(text, key)}
          </h3>,
        );
      } else {
        blocks.push(
          <h4 key={key} id={anchorId} className="scroll-mt-24 text-body font-bold text-text-primary">
            {renderInline(text, key)}
          </h4>,
        );
      }
      i += 1;
      continue;
    }

    if (/^[-*]\s+/.test(line)) {
      const items: string[] = [];
      while (i < lines.length && /^[-*]\s+/.test(lines[i] ?? "")) {
        items.push((lines[i] ?? "").replace(/^[-*]\s+/, ""));
        i += 1;
      }
      const key = `ul-${String(blockKey++)}`;
      blocks.push(
        <ul key={key} className="list-disc space-y-1 pl-5 text-body text-text-secondary">
          {items.map((item, idx) => (
            <li key={`${key}-${String(idx)}`}>{renderInline(item, `${key}-${String(idx)}`)}</li>
          ))}
        </ul>,
      );
      continue;
    }

    if (/^>\s+/.test(line)) {
      const quoteLines: string[] = [];
      while (i < lines.length && /^>\s+/.test(lines[i] ?? "")) {
        quoteLines.push((lines[i] ?? "").replace(/^>\s+/, ""));
        i += 1;
      }
      const key = `bq-${String(blockKey++)}`;
      blocks.push(
        <blockquote key={key} className="border-l-4 border-border pl-4 text-body italic text-text-secondary">
          {quoteLines.join(" ")}
        </blockquote>,
      );
      continue;
    }

    const paragraphLines: string[] = [];
    while (
      i < lines.length &&
      (lines[i] ?? "").trim() !== "" &&
      !/^(#{1,3})\s+/.test(lines[i] ?? "") &&
      !/^[-*]\s+/.test(lines[i] ?? "") &&
      !/^>\s+/.test(lines[i] ?? "")
    ) {
      paragraphLines.push(lines[i] ?? "");
      i += 1;
    }
    const key = `p-${String(blockKey++)}`;
    blocks.push(
      <p key={key} className="text-body text-text-secondary">
        {renderInline(paragraphLines.join(" "), key)}
      </p>,
    );
  }

  return <div className="flex flex-col gap-4">{blocks}</div>;
}
