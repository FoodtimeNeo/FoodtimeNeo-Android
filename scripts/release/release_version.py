"""Map the displayed release name to metadata without dropping its test-stage suffix."""
import argparse
import json
import re


def release_version(name, code):
    match = re.fullmatch(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)([αβ])?", name)
    if not match or not re.fullmatch(r"[1-9][0-9]*", str(code)) or int(code) > 2100000000:
        raise ValueError("Invalid display version or Android versionCode")
    base = ".".join(match.group(i) for i in range(1, 4))
    stage = {None: "stable", "α": "alpha", "β": "beta"}[match.group(4)]
    return {"version": name, "code": int(code), "tag": "v" + name,
            "channel": stage, "prerelease": stage != "stable",
            "internalVersion": base if stage == "stable" else base + "-" + stage}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("name")
    parser.add_argument("code")
    parser.add_argument("--github-output", action="store_true")
    args = parser.parse_args()
    value = release_version(args.name, args.code)
    if args.github_output:
        for key, item in value.items():
            print(f"{key}={str(item).lower() if isinstance(item, bool) else item}")
    else:
        print(json.dumps(value, ensure_ascii=False))
