defguard createSliver(?SliceId) :-
  spec("Guard for Aggregate.createSliver"),
  ?PlainBearerRef := decrypt("AES", $BearerRef),
  ?PolicyRef := label("aggregate/standard-slice-operations"),
  {
    link($MyAnchorSet).
    link($PolicyRef).
    link($PlainBearerRef). 
    approveSliver($Subject, $SliceId)?
  }.

defun mintAESKey(?KeyLen) :-
  ?AESKey := createAESKey(?KeyLen),
  `
     val key = $AESKey
     println(s"key: $key")
     key                 
  `.
