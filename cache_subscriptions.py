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

if len(sys.argv) < 2:
    sys.stderr.write('Usage: <subscriptsion.xml>')
    sys.exit(1)

if not os.path.exists(sys.argv[1]):
    sys.stderr.write('ERROR: subscriptions .xml file not found!')
    sys.exit(1)

f = open( sys.argv[1], 'r' )
doc = xmltodict.parse( f.read() )

def time_to_list( t ):
    return [t.tm_year, t.tm_mon, t.tm_mday, t.tm_hour, t.tm_min, t.tm_sec]

def insert_entries( entries, feed ):

    sys.stdout.write( "Getting %s..." % feed['url'] )
    d = feedparser.parse( feed['url'] )
    sys.stdout.write( "done\n" )

    print d.feed.get( 'title', 'No title' ), \
          d.feed.get( 'link', 'No link' ), \
          len(d.entries), 'entries'

    for e in d.entries:
        if 'published_parsed' in e and 'link' in e:
            title   = e.get( 'title', 'No title' )
            link    = e['link']
            id      = sha.new(link).hexdigest()[:8]
            entry   = entries.get( id, {} )

            sys.stdout.write( "Getting %s..." % link );

            entry.update({  
                '_id'       : id, 
                'type'      : 'entry',
                'feed_id'   : feed['_id'],
                'title'     : title, 
                'link'      : link, 
                'read'      : random.getrandbits(1),
                'starred'   : random.getrandbits(1),
                'shared'    : random.getrandbits(1),
                'published' : time_to_list( e.published_parsed ), 
                'content'   : requests.get( link ).text,
            })

            entries[id] = entry
            sys.stdout.write( "done.\n" )

def traverse_subs( feeds, entries, subs ):
    traverse_subs( feeds, entries, None, subs )
    
def traverse_subs( feeds, entries, folder, subs ):
    for s in subs:
        if '@type' in s:
            url = s['@xmlUrl'] 
            id = sha.new( url ).hexdigest()[:8]
            feed = feeds.get( id, {} );
            feed.update({   
                '_id'       : id,
                'type'      : 'feed',
                'title'     : s['@title'],
                'url'       : url,
                'folders'   : feed.get('folders', []) + [folder],
                'unread_count' : 0,
            });
            feeds[id] = feed
            insert_entries( entries, feed )
        else:
            print s['@title']
            traverse_subs( feeds, entries, s['@title'], s['outline'] )

subs = doc['opml']['body']['outline']

feeds = {}
entries = {}
traverse_subs( feeds, entries, None, subs )

sys.stdout.write( "Writing feed data into cache.dat..." )
marshal.dump( {'feeds' : feeds, 'entries' : entries}, open( 'cache.dat', 'wb' ) )
sys.stdout.write( "done\n" )

