<?php
/*
 * #%L
 * ImageJ software for multidimensional image processing and analysis.
 * %%
 * Copyright (C) 2009 - 2014 Board of Regents of the University of
 * Wisconsin-Madison, Broad Institute of MIT and Harvard, and Max Planck
 * Institute of Molecular Cell Biology and Genetics.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

/* Parses incoming statistics, storing them in the DB. */
function processStats($data) {
	header('Content-Type: application/json');

	$output = array();

	if (strlen($data) == 0) {
		$output['message'] = 'No statistics to process';
		print json_encode($output);
		return;
	}

	$json = json_decode($data, true);

	$db = connectToDB();
	if (!$db) {
		$output['message'] = 'Cannot connect to database';
		print json_encode($output);
		return;
	}
	$timestamp = date('Y-m-d H:i:s');
	$user = value($json, 'user');
	$ip_address = $_SERVER['REMOTE_ADDR'];
	$event_id = insertEvent($db, $timestamp, $user, $ip_address);

	foreach ($json['sites'] as &$site) {
		$site_id = lookupSite($db, $site);
		foreach ($site['stats'] as &$stat) {
			insertStat($db, $event_id, $stat, $site_id);
		}
	}

	$db->close();

	$output['message'] = 'Statistics processed';
	print json_encode($output);
}

// -- Database functions --

/* Connects to the MySQL database, creating relevant tables as needed. */
function connectToDB() {
	require('/var/www/vhosts/usage.imagej.net/conf/config.php');
	$db = new mysqli($mysql_server, $mysql_user, $mysql_pass, $mysql_db);
	if ($db->connect_errno) return null;

	createTable($db, "sites", "site_id",
		"name TINYTEXT, url TINYTEXT");
	createTable($db, "objects", "object_id",
		"identifier TINYTEXT, site_id INT, version TINYTEXT, " .
		"name TINYTEXT, label TINYTEXT, description TEXT");
	createTable($db, "events", "event_id",
		"timestamp DATETIME, user TINYTEXT, ip_address TINYTEXT");
	createTable($db, "stats", "stat_id",
		"event_id INT, object_id INT, count INT");

	return $db;
}

/* Creates a table, if it does not already exist. */
function createTable($db, $table_name, $id_column, $columns) {
	$sql = "CREATE TABLE IF NOT EXISTS $table_name (" .
		"$id_column INT NOT NULL AUTO_INCREMENT, " .
		"PRIMARY KEY($id_column), $columns)";
	$result = $db->query($sql);
	if (!$result) die("Error creating table: $db->error");
}

/* Gets the site_id of an update site, creating it if needed. */
function lookupSite($db, $site) {
	$name = value($site, 'name');
	$url = value($site, 'url');

	$statement = $db->prepare("SELECT site_id FROM sites " .
		"WHERE name = ? AND url = ?");
	$statement->bind_param('ss', $name, $url);
	$site_id = select($statement, 'site_id');
	return $site_id ? $site_id : insertSite($db, $site);
}

/* Inserts a row into the sites table, returning the new site ID. */
function insertSite($db, $site) {
	$name = value($site, 'name');
	$url = value($site, 'url');

	$statement = $db->prepare("INSERT INTO sites (name, url) VALUES (?, ?)");
	$statement->bind_param('ss', $name, $url);
	return insert($db, $statement);
}

/* Inserts a row into the events table, returning the new event ID. */
function insertEvent($db, $timestamp, $user, $ip_address) {
	$statement = $db->prepare("INSERT INTO events " .
		"(timestamp, user, ip_address) VALUES (?, ?, ?)");
	$statement->bind_param('sss', $timestamp, $user, $ip_address);
	return insert($db, $statement);
}

/** Inserts a row into the stats table, returning the new stat ID. */
function insertStat($db, $event_id, $stat, $site_id) {
	$identifier = value($stat, 'id');
	$object_id = lookupObject($db, $stat, $site_id);
	$count = value($stat, 'count');

	$statement = $db->prepare("INSERT INTO stats " .
		"(event_id, object_id, count) VALUES (?, ?, ?)");
	$statement->bind_param('iii', $event_id, $object_id, $count);
	return insert($db, $statement);
}

/* Gets the object_id of an object identifier, creating it if needed. */
function lookupObject($db, $stat, $site_id) {
	$identifier = value($stat, 'id');
	$version = value($stat, 'version');

	$statement = $db->prepare("SELECT object_id FROM objects " .
		"WHERE identifier = ? AND version = ?");
	$statement->bind_param('ss', $identifier, $version);
	$object_id = select($statement, 'object_id');
	return $object_id ? $object_id : insertObject($db, $stat, $site_id);
}

/* Inserts a row into the objects table, returning the new object ID. */
function insertObject($db, $stat, $site_id) {
	$identifier = value($stat, 'id');
	$version = value($stat, 'version');
	$name = value($stat, 'name');
	$label = value($stat, 'label');
	$description = value($stat, 'description');

	$statement = $db->prepare("INSERT INTO objects " .
		"(identifier, site_id, version, name, label, description) " .
		"VALUES (?, ?, ?, ?, ?, ?)");
	$statement->bind_param('sissss',
		$identifier, $site_id, $version, $name, $label, $description);
	return insert($db, $statement);
}

/* Executes a database INSERT. */
function insert($db, $statement) {
	$success = $statement->execute();
	$value = $success ? $db->insert_id : null;
	$statement->close();
	return $value;
}

/* Executes a database SELECT. */
function select($statement, $column) {
	$success = $statement->execute();
	$statement->bind_result($value);
	$statement->fetch();
	$statement->close();
	return $value;
}

/* Gets the value of the given key in the specified array, or null if none. */
function value($array, $key) {
	return $array && array_key_exists($key, $array) ? $array[$key] : null;
}

// -- Main function --

function main() {
	$data = file_get_contents('php://input');
	processStats($data);
}

main();
?>
