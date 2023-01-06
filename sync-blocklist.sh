#!/bin/sh

echo "Syncing default blocklist..."

cd landing-github-pages
git checkout master
git pull

hash=$(git describe --abbrev=4 --always --tags --dirty)
commit="sync: update default blocklist to: $hash"

echo $commit

grep -v "covid" mirror/v5/oisd/light/hosts.txt > cleaned-hosts.txt
cp cleaned-hosts.txt ../app/src/libre/assets/default_blocklist
rm cleaned-hosts.txt
cd ../app/src/libre/assets/
rm default_blocklist.zip
zip -e default_blocklist.zip default_blocklist
rm default_blocklist
cd ../../../

git commit -am "$commit"

echo "Done"
