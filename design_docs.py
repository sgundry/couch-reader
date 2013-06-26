design_docs = [
    { 
        "_id": "_design/entry",
        "language": "javascript",
        "views": {
            "by_id": {
                "map": '''function(doc) {\n  if(doc.type == 'entry') {\n    emit(doc._id, doc);\n  }\n}'''
            },
            "unread_by_id": {
                "map": '''function(doc) {\n  if(doc.type == 'entry' && doc.read == false) {\n    emit(doc._id, doc);\n  }\n}'''
            }
        }
    },
    {
       "_id": "_design/feed",
       "language": "javascript",
       "views": {
           "by_id": {
               "map": '''function(doc) {\n  if(doc.type == 'feed') {\n    emit(doc._id, doc);\n  }\n}'''
           }
       }
    },
    {
       "_id": "_design/show",
       "shows": {
           "summary": "function(doc, req){ return '<h1>' + doc.title + '</h1>'; }",
           "content": "function(doc, req){ return doc.content; }"
       }
    }
]
