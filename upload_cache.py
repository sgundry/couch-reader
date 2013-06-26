import sys
import os
import xmltodict
import json
import requests
import feedparser
import time
import sha
import calendar 
import random
import marshal

if len(sys.argv) < 3:
    sys.stderr.write('Usage: <username> <cache.dat>')
    sys.exit(1)

if not os.path.exists(sys.argv[2]):
    sys.stderr.write('ERROR: %s not found!' % sys.argv[1])
    sys.exit(1)

cache_file = sys.argv[2]
sys.stdout.write( "Loading feed data from %s..." % cache_file )
cache = marshal.load( open( cache_file, 'rb' ) )
sys.stdout.write( "done.\n" )

feeds           = cache['feeds']
entries         = cache['entries']
username        = sys.argv[1]
email           = username + '@gmail.com'
username_sha    = sha.new(username).hexdigest()[:8]
db              = 'u' + username_sha
db_url          = 'http://127.0.0.1:5984/%s/' % db 

# ensure user database exists
requests.put( db_url, "{}" )

def get( id ):
    global db_url
    url = db_url + id

    sys.stdout.write( "Getting %s..." % url )
    r = requests.get( url )
    if r.status_code == requests.codes.ok:
        sys.stdout.write( "done.\n" )
    elif r.json()['error'] == 'not_found':
        sys.stdout.write( "not found.\n" )
        return {}
    else:
        sys.stderr.write( "Failed: code=%d doc=%s\n" % (r.status_code, r.json()) )
        r.raise_for_status()

    return r.json()

def put( doc ):
    global db_url
    url = db_url + doc['_id']

    # Get the existing doc and update it 
    doc.update( get( doc['_id'] ) )
    
    sys.stdout.write( "Putting %s..." % url )
    r = requests.put( url, data=json.dumps( doc ) )
    r.raise_for_status()
    sys.stdout.write( "done.\n" )

for id, feed in feeds.items():
    put( feed )
    
for id, entry in entries.items():
    put( entry )

from design_docs import *
for d in design_docs:
    put( d )

