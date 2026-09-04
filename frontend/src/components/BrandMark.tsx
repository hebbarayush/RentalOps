import { useState } from "react";

/**
 * The RentalOps mark. Renders the real logo when its asset is present (it's a local-only
 * file — see .gitignore — so it's not shipped in the public repo); falls back to a plain
 * text badge on any environment that doesn't have it, so the UI never shows a broken image.
 */
export function BrandMark() {
  const [broken, setBroken] = useState(false);

  if (broken) {
    return (
      <span className="brand-mark brand-mark--fallback" aria-hidden="true">
        R
      </span>
    );
  }

  return (
    <img
      className="brand-mark"
      src="/brand/icon-512.png"
      alt=""
      aria-hidden="true"
      onError={() => setBroken(true)}
    />
  );
}
