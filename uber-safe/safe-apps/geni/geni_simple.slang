defenv Selfie :-
  spec('Load the principal keypair'),
  principal($1),
end

defenv GeniRoot :- u'ouLCPTetVJYnAUbfE0n_W6ElYxITXG8lKD6bPCYlbsM'.

(*
defenv Idp      :- u'As1r56WiIHkxzci8valHOGyDYNXD4GPWbmMqbHDXwp8'.
defenv PA       :- u'q-O1jJKpE7ZMVyf-3nKhYgEOjGvE16UJtA6h45DxgKE'.
defenv SA       :- u'825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44'.
defenv CP       :- u'ay1HJXe5v38PG0uGOgU10G2Pstn3wcE9-7mcXb5JmxQ'.

defenv PI       :- u'8Hlq_5XH7C25YCpPklHk3XNn4B-asWRqiAdPJdyXFVY'.
defenv User1    :- u'1RF6d7jpvAVVsByofu7XLx4-O9Qhbd5eXLGhFSGQTps'.
defenv User2    :- u'DCR9fcoV0bKPv6ZybvLBnKP7mtDdCtIE1xger5P416s'.
*)

defetch fetchAllLinks() :-
  ?TrustStructure  := computeIdFromName($Self, 'subject/trust-structure'),
  ?GeniEndorsement := computeIdFromName($GeniRoot, 'endorseAll'),
  [?TrustStructure, ?GeniEndorsement],
end

defguard authorize() :-
  spec('authorize the subject to access the slice on given project'),
  ?CPPolicySet    := computeIdFromName($Self, 'object/aggregate-policy-slice-operations'),
  ?FetchedSets    := fetchAllLinks(),
  println('Printing fetchedSet links'),
  println(?FetchedSets),
  {
    importAll($FetchedSets).

    geniRoot($GeniRoot).

    sliceAuthority(?X)?
    hello(world).
    hello(?X)?
  },
end
 
definit authorize().
