defenv GeniRoot :- u'ouLCPTetVJYnAUbfE0n_W6ElYxITXG8lKD6bPCYlbsM'.
defenv Idp      :- u'As1r56WiIHkxzci8valHOGyDYNXD4GPWbmMqbHDXwp8'.
defenv PA       :- u'q-O1jJKpE7ZMVyf-3nKhYgEOjGvE16UJtA6h45DxgKE'.
defenv SA       :- u'825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44'.
defenv CP       :- u'ay1HJXe5v38PG0uGOgU10G2Pstn3wcE9-7mcXb5JmxQ'.

defenv PI       :- u'8Hlq_5XH7C25YCpPklHk3XNn4B-asWRqiAdPJdyXFVY'.
defenv User1    :- u'1RF6d7jpvAVVsByofu7XLx4-O9Qhbd5eXLGhFSGQTps'.
defenv User2    :- u'DCR9fcoV0bKPv6ZybvLBnKP7mtDdCtIE1xger5P416s'.

defenv EndorsePISet    :- computeIdFromName('As1r56WiIHkxzci8valHOGyDYNXD4GPWbmMqbHDXwp8', 'endorse/pi/8Hlq_5XH7C25YCpPklHk3XNn4B-asWRqiAdPJdyXFVY').
defenv EndorseUserSet  :- computeIdFromName('As1r56WiIHkxzci8valHOGyDYNXD4GPWbmMqbHDXwp8', 'endorse/user/1RF6d7jpvAVVsByofu7XLx4-O9Qhbd5eXLGhFSGQTps').
defenv ProjectRef      :- '8Hlq_5XH7C25YCpPklHk3XNn4B-asWRqiAdPJdyXFVY:projectGuid'.
defenv ProjectSet      :- computeIdFromName('q-O1jJKpE7ZMVyf-3nKhYgEOjGvE16UJtA6h45DxgKE',  'capability/8Hlq_5XH7C25YCpPklHk3XNn4B-asWRqiAdPJdyXFVY:projectGuid/8Hlq_5XH7C25YCpPklHk3XNn4B-asWRqiAdPJdyXFVY').
defenv SliceRef        :- '825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44:sliceGuid'.
defenv SliceSet        :- computeIdFromName('825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44',  'delegate/1RF6d7jpvAVVsByofu7XLx4-O9Qhbd5eXLGhFSGQTps/825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44:sliceGuid').
defenv TrustStructure  :- computeIdFromName('ay1HJXe5v38PG0uGOgU10G2Pstn3wcE9-7mcXb5JmxQ', 'subject/trust-structure').
defenv RegisteredUser  :- computeIdFromName('ay1HJXe5v38PG0uGOgU10G2Pstn3wcE9-7mcXb5JmxQ', 'subject/registered-user').
defenv GeniEndorsement :- computeIdFromName('ouLCPTetVJYnAUbfE0n_W6ElYxITXG8lKD6bPCYlbsM', 'endorseAll').
defenv CPPolicySet     :- computeIdFromName('ay1HJXe5v38PG0uGOgU10G2Pstn3wcE9-7mcXb5JmxQ', 'object/aggregate-policy-slice-operations').

defetch fetchAllLinks() :-
  [$EndorsePISet, $EndorseUserSet, $ProjectSet, $SliceSet, $TrustStructure, $RegisteredUser, $GeniEndorsement].

defcon aggregatePolicySet() :- 
  {
     sliverInstantiate(?Slice, ?Subject, ?Project) :-
       ?SA := rootId(?Slice),
       ?SA: slice(?Slice, ?Project, standard),
       sliceAuthority(?SA), 
       ?SA: controlPrivilege(?Subject, ?Slice, instantiate, _),
       geniUser(?Subject).
 
     name('object/aggregate-policy-slice-operations').
  }.

//defenv GlobalFetchedSets :- fetchAllLinks().

defcon collectSet() :- 
  collectedImport{
     link($EndorsePISet).
     link($EndorseUserSet).
     link($ProjectSet).
     link($SliceSet).
     link($TrustStructure).
     link($RegisteredUser).
     link($GeniEndorsement).
  }
end

(*
     import($EndorsePISet).
     import($EndorseUserSet).
     import($ProjectSet).
     import($SliceSet).
     import($TrustStructure).
     import($RegisteredUser).
     import($GeniEndorsement).
     //import($CPPolicySet). // TODO
*)

defguard authorize() :-
  spec('authorize the subject to access the slice on given project'),
  ?FetchedSets    := fetchAllLinks(),
  {
     importAll($FetchedSets).

     sliverInstantiate(?Slice, ?Subject, ?Project) :-
       geniUser(?Subject),
       ?SA := rootId(?Slice),
       ?SA: slice(?Slice, ?Project, standard),
       sliceAuthority(?SA), 
       ?SA: controlPrivilege(?Subject, ?Slice, instantiate, _).

(*
     sliverInstantiate(?Slice, ?Subject, ?Project) :-
       "?Subject; ?Project; ?Slice"
     end


    sliverInstantiate(?Slice, ?Subject, ?Project) :-
       geniUser(?Subject),
       "?Project; ?Slice"
     end


       ?SA := rootId(?Slice),
       sliceAuthority(?SA), 
       ?SA: slice(?Slice, ?Project, standard),
       ?SA: controlPrivilege(?Subject, ?Slice, instantiate, _),
*)

     geniRoot($GeniRoot).
 
     sliverInstantiate($SliceRef, $User1, $ProjectRef)?
  }
end

defguard authorizeWithFetch() :-
  spec('authorize the subject to access the slice on given project'),
  ?Subject        := "$User1",
  ?FetchedSets    := fetchAllLinks(),
  {
     importAll($FetchedSets).
     //import($CPPolicySet). // TODO

     sliverInstantiate(?Slice, ?Subject, ?Project) :-
       ?SA := rootId(?Slice),
       ?SA: slice(?Slice, ?Project, standard),
       sliceAuthority(?SA), 
       ?SA: controlPrivilege(?Subject, ?Slice, instantiate, _),
       geniUser(?Subject).

     geniRoot($GeniRoot).
 
     sliverInstantiate($SliceRef, $Subject, $ProjectRef)?
  }
end
