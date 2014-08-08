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
	$ip_address = $_SERVER['REMOTE_ADDR'];

	$user_id = lookupUser($db, $json);
	$country_id = lookupCountry($db, $json);
	$language_id = lookupLanguage($db, $json);
	$timezone_id = lookupTimezone($db, $json);
	$os_id = lookupOS($db, $json);
	$java_id = lookupJava($db, $json);

	$event_id = insertEvent($db, $timestamp, $ip_address, $user_id,
		$country_id, $language_id, $timezone_id, $os_id, $java_id);

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

	// list of users
	createTable($db, "users", "user_id", "user TINYTEXT");

	// list of countries
	createTable($db, "countries", "country_id", "name TINYTEXT");

	// list of languages
	createTable($db, "languages", "language_id", "name TINYTEXT");

	// list of timezones
	createTable($db, "timezones", "timezone_id", "name TINYTEXT");

	// operating systems
	createTable($db, "os", "os_id",
		"name TINYTEXT, arch TINYTEXT, version TINYTEXT");

	// java installation profiles
	createTable($db, "java", "java_id",
		"runtime_name TINYTEXT, runtime_version TINYTEXT, " .
		"spec_name TINYTEXT, spec_vendor TINYTEXT, spec_version TINYTEXT, " .
		"vendor TINYTEXT, version TINYTEXT, " .
		"vm_name TINYTEXT, vm_vendor TINYTEXT, vm_version TINYTEXT, " .
		"vm_spec_name TINYTEXT, vm_spec_vendor TINYTEXT, vm_spec_version TINYTEXT");

	// ImageJ update sites
	createTable($db, "sites", "site_id", "name TINYTEXT, url TINYTEXT");

	// identifiable objects (e.g., plugins)
	createTable($db, "objects", "object_id",
		"identifier TINYTEXT, site_id INT, version TINYTEXT, " .
		"name TINYTEXT, label TINYTEXT, description TEXT");

	// upload events (one row each time ImageJ uploads a batch of statistics)
	createTable($db, "events", "event_id",
		"timestamp DATETIME, ip_address TINYTEXT, user_id INT, country_id INT, " .
		"language_id INT, timezone_id INT, os_id INT, java_id INT");

	// usage counts (per object, per event)
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

/* Gets the user_id of a user, creating it if needed. */
function lookupUser($db, $json) {
	$user = value($json, 'user');

	$statement = $db->prepare("SELECT user_id FROM users WHERE user = ?");
	$statement->bind_param('s', $user);
	$user_id = select($statement, 'user_id');
	if ($user_id) return $user_id;

	$statement = $db->prepare("INSERT INTO users (user) VALUES (?)");
	$statement->bind_param('s', $user);
	return insert($db, $statement);
}

/* Gets the country_id of a country, creating it if needed. */
function lookupCountry($db, $json) {
	$name = value($json, 'user_country');

	$statement = $db->prepare("SELECT country_id FROM countries WHERE name = ?");
	$statement->bind_param('s', $name);
	$country_id = select($statement, 'country_id');
	if ($country_id) return $country_id;

	$statement = $db->prepare("INSERT INTO countries (name) VALUES (?)");
	$statement->bind_param('s', $name);
	return insert($db, $statement);
}

/* Gets the language_id of a language, creating it if needed. */
function lookupLanguage($db, $json) {
	$name = value($json, 'user_language');

	$statement = $db->prepare("SELECT language_id FROM languages WHERE name = ?");
	$statement->bind_param('s', $name);
	$language_id = select($statement, 'language_id');
	if ($language_id) return $language_id;

	$statement = $db->prepare("INSERT INTO languages (name) VALUES (?)");
	$statement->bind_param('s', $name);
	return insert($db, $statement);
}

/* Gets the timezone_id of a timezone, creating it if needed. */
function lookupTimezone($db, $json) {
	$name = value($json, 'user_timezone');

	$statement = $db->prepare("SELECT timezone_id FROM timezones WHERE name = ?");
	$statement->bind_param('s', $name);
	$timezone_id = select($statement, 'timezone_id');
	if ($timezone_id) return $timezone_id;

	$statement = $db->prepare("INSERT INTO timezones (name) VALUES (?)");
	$statement->bind_param('s', $name);
	return insert($db, $statement);
}

/* Gets the os_id of an OS configuration, creating it if needed. */
function lookupOS($db, $json) {
	$name = value($json, 'os_name');
	$arch = value($json, 'os_arch');
	$version = value($json, 'os_version');

	$statement = $db->prepare("SELECT os_id FROM os WHERE " .
		"name = ? AND arch = ? AND version = ?");
	$statement->bind_param('sss', $name, $arch, $version);
	$os_id = select($statement, 'os_id');
	if ($os_id) return $os_id;

	$statement = $db->prepare("INSERT INTO os " .
		"(name, arch, version) VALUES (?, ?, ?)");
	$statement->bind_param('sss', $name, $arch, $version);
	return insert($db, $statement);
}

/* Gets the java_id of a java configuration, creating it if needed. */
function lookupJava($db, $json) {
	$runtime_name = value($json, 'java_runtime_name');
	$runtime_version = value($json, 'java_runtime_version');
	$spec_name = value($json, 'java_specification_name');
	$spec_vendor = value($json, 'java_specification_vendor');
	$spec_version = value($json, 'java_specification_version');
	$vendor = value($json, 'java_vendor');
	$version = value($json, 'java_version');
	$vm_name = value($json, 'java_vm_name');
	$vm_vendor = value($json, 'java_vm_vendor');
	$vm_version = value($json, 'java_vm_version');
	$vm_spec_name = value($json, 'java_vm_specification_name');
	$vm_spec_vendor = value($json, 'java_vm_specification_vendor');
	$vm_spec_version = value($json, 'java_vm_specification_version');

	$statement = $db->prepare("SELECT java_id FROM java WHERE " .
		"runtime_name = ? AND runtime_version = ? AND " .
		"spec_name = ? AND spec_vendor = ? AND spec_version = ? AND " .
		"vendor = ? AND version = ? AND " .
		"vm_name = ? AND vm_vendor = ? AND vm_version = ? AND " .
		"vm_spec_name = ? AND vm_spec_vendor = ? AND vm_spec_version = ?");
	$statement->bind_param('sssssssssssss',
		$runtime_name, $runtime_version,
		$spec_name, $spec_vendor, $spec_version,
		$vendor, $version,
		$vm_name, $vm_vendor, $vm_version,
		$vm_spec_name, $vm_spec_vendor, $vm_spec_version);
	$java_id = select($statement, 'java_id');
	if ($java_id) return $java_id;

	$statement = $db->prepare("INSERT INTO java " .
		"(runtime_name, runtime_version, " .
		"spec_name, spec_vendor, spec_version, " .
		"vendor, version, " .
		"vm_name, vm_vendor, vm_version, " .
		"vm_spec_name, vm_spec_vendor, vm_spec_version) " .
		"VALUES (?, ?, " . // runtime: name, version
		"?, ?, ?, " . // spec: name, vendor, version
		"?, ?, " . // vendor, version
		"?, ?, ?, " . // vm: name, vendor, version
		"?, ?, ?)"); // vm_spec: name, vendor, version
	$statement->bind_param('sssssssssssss',
		$runtime_name, $runtime_version,
		$spec_name, $spec_vendor, $spec_version,
		$vendor, $version,
		$vm_name, $vm_vendor, $vm_version,
		$vm_spec_name, $vm_spec_vendor, $vm_spec_version);
	return insert($db, $statement);
}

/* Gets the site_id of an update site, creating it if needed. */
function lookupSite($db, $site) {
	$name = value($site, 'name');
	$url = value($site, 'url');

	$statement = $db->prepare("SELECT site_id FROM sites " .
		"WHERE name = ? AND url = ?");
	$statement->bind_param('ss', $name, $url);
	$site_id = select($statement, 'site_id');
	if ($site_id) return $site_id;

	$statement = $db->prepare("INSERT INTO sites (name, url) VALUES (?, ?)");
	$statement->bind_param('ss', $name, $url);
	return insert($db, $statement);
}

/* Inserts a row into the events table, returning the new event ID. */
function insertEvent($db, $timestamp, $ip_address, $user_id,
	$country_id, $language_id, $timezone_id, $os_id, $java_id)
{
	$statement = $db->prepare("INSERT INTO events " .
		"(timestamp, ip_address, user_id, " .
		"country_id, language_id, timezone_id, os_id, java_id) " .
		"VALUES (?, ?, ?, " . // timestamp, ip_address, user_id
		"?, ?, ?, ?, ?)"); // country_id, language_id, timezone_id, os_id, java_id
	$statement->bind_param('ssiiiiii',
		$timestamp, $ip_address, $user_id,
		$country_id, $language_id, $timezone_id, $os_id, $java_id);
	return insert($db, $statement);
}

/* Inserts a row into the stats table, returning the new stat ID. */
function insertStat($db, $event_id, $stat, $site_id) {
	$identifier = value($stat, 'id');
	$object_id = lookupObject($db, $stat, $site_id);
	$count = value($stat, 'count');

	// NB: Purge sensitive details uploaded from early versions of imagej-usage.
	// While these versions were only available for a couple of hours,
	// let's avoid polluting the objects table with things like file paths.
	//
	// This regex culls any legacy arg containing a forward slash.
	preg_replace('/^(legacy:[^?]*)\?.*\/.*$/', '$1', $identifier);

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

/* Gets the value of the given key in the specified array, or '' if none. */
function value($array, $key) {
	return $array && array_key_exists($key, $array) ? $array[$key] : '';
}

// -- Main function --

function main() {
	$data = file_get_contents('php://input');
	processStats($data);
}

main();
?>
