#!/bin/sh
# NB: this is dangerous script. must be check and confirm.

CLAN_DIR="clan"
SRC_DIR="sample"

if [ ! -e ${CLAN_DIR} ]; then
        echo "please check https://github.com/ayamada/clan ."
        exit 1
fi

echo "SPREADING TO CURRENT DIRECTORY from sample-project file of clan."
echo "start at after 5 secs."
sleep 1
echo "start at after 4 secs."
sleep 1
echo "start at after 3 secs."
sleep 1
echo "start at after 2 secs."
sleep 1
echo "start at after 1 secs."
sleep 1
echo "start."

if [ ! -e ${CLAN_DIR}/${SRC_DIR} ]; then
        echo "something is wrong, aborted."
        exit 1
fi

TARGETS=`ls ${CLAN_DIR}/${SRC_DIR}`

for f in ${TARGETS}; do
        if [ -e $f ]; then
                echo "target file $f already exists, aborted."
                exit 1
        fi
done

for f in ${TARGETS}; do
        echo "cp -a ${CLAN_DIR}/${SRC_DIR}/$f ."
        cp -a ${CLAN_DIR}/${SRC_DIR}/$f .
done

# .gitignore
echo "cat ${CLAN_DIR}/${SRC_DIR}/.gitignore >> .gitignore"
cat ${CLAN_DIR}/${SRC_DIR}/.gitignore >> .gitignore


echo "done."


