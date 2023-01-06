#!/bin/sh

echo "Syncing default blocklist"

grep -v "covid" ../landing-github-pages/mirror/v5/oisd/light/hosts.txt > cleaned-hosts.txt
cp cleaned-hosts.txt app/src/main/assets/default_blocklist
rm cleaned-hosts.txt
cd app/src/main/assets/
rm default_blocklist.zip
zip -e default_blocklist.zip default_blocklist
rm default_blocklist
cd ../../../
echo "Done"
git add .
git ci -am "sync: update default blocklist"
