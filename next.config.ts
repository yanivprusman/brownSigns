import type { NextConfig } from "next";
import path from "path";
import fs from "fs";

// @automatelinux/geo lives in the automateLinux npm workspace, two levels up from
// /opt/dev/<app>. Turbopack's root must contain both the app and the package it
// links to; fail loud when the workspace is not where the file: dependency says.
const turbopackRoot = path.resolve(process.cwd(), "../../");
const workspaceRoot = path.resolve(turbopackRoot, "automateLinux");
if (!fs.existsSync(path.join(workspaceRoot, "packages/geo/package.json"))) {
  throw new Error(
    `@automatelinux/geo workspace guard: expected the workspace at ${workspaceRoot} ` +
      `(derived from Turbopack root ${turbopackRoot}).`,
  );
}

const nextConfig: NextConfig = {
  allowedDevOrigins: process.env.ALLOWED_DEV_ORIGINS?.split(',') ?? [],
  turbopack: { root: turbopackRoot },
  outputFileTracingRoot: turbopackRoot,
  transpilePackages: ["@automatelinux/geo"],
};

export default nextConfig;
