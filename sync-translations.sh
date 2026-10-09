#!/bin/sh

echo "Syncing strings..."

cd translate/scripts
git checkout master
git pull
hash=$(git describe --abbrev=4 --always --tags --dirty)
commit="sync: update translate strings to: $hash"

echo $commit

# translate.py defaults to a target three levels up, for the app repo layout.
./translate.py -a android5 -t ../..

cd ../../

git commit -am "$commit"

echo "Done"
