#!/bin/bash
IFS=. read major minor patch <<< "$(git describe --tags --abbrev=0)"

echo "${major}"
echo "${minor}"
echo "${patch}"

newTag="${major}.${minor}.${patch}"
echo "Old tag: ${newTag}"

if [[ $1 == "major" ]]; then
     newTag="$((major + 1)).0.0"
elif [[ $1 == "minor" ]]; then
     newTag="${major}.$((minor + 1)).0"
else
     newTag="${major}.${minor}.$((patch + 1))"
fi
echo "New tag ${newTag}"

git tag "$newTag"
git push origin tag "$newTag"