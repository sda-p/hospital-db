#!/bin/zsh

if [ "$#" -ne 2 ]; then
  echo "Usage: ./scripts/import_database.sh <mysql-user> <database-name>"
  exit 1
fi

MYSQL_USER="$1"
DATABASE_NAME="$2"

mysql -u "$MYSQL_USER" -p -e "CREATE DATABASE IF NOT EXISTS \`$DATABASE_NAME\`;"
mysql -u "$MYSQL_USER" -p "$DATABASE_NAME" < hospital_new_new.sql
