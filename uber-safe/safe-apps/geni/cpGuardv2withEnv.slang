defenv Selfie :-
  spec('Load the principal keypair'),
  principal('/home/vamsi/Code/repo/wowmsi/safe/safe-apps/geni/cp_keyPair.pem')
end

defenv GeniRoot :- u'ouLCPTetVJYnAUbfE0n_W6ElYxITXG8lKD6bPCYlbsM'.
defenv Idp      :- u'As1r56WiIHkxzci8valHOGyDYNXD4GPWbmMqbHDXwp8'.
defenv PA       :- u'q-O1jJKpE7ZMVyf-3nKhYgEOjGvE16UJtA6h45DxgKE'.
defenv SA       :- u'825QDOgLcetdp4qOvGZRc9nTqSuQPQdRmEg5j-nXG44'.
defenv CP       :- u'ay1HJXe5v38PG0uGOgU10G2Pstn3wcE9-7mcXb5JmxQ'.

defenv PI       :- u'8Hlq_5XH7C25YCpPklHk3XNn4B-asWRqiAdPJdyXFVY'.
defenv User1    :- u'1RF6d7jpvAVVsByofu7XLx4-O9Qhbd5eXLGhFSGQTps'.
defenv User2    :- u'DCR9fcoV0bKPv6ZybvLBnKP7mtDdCtIE1xger5P416s'.

defenv EndorsePISet    :- computeIdFromName($Idp, "endorse/pi/$PI").
defenv EndorseUserSet  :- computeIdFromName($Idp, "endorse/user/$User1").
defenv ProjectRef      :- "$PI:projectGuid".
defenv ProjectSet      :- computeIdFromName($PA,  "capability/?ProjectRef/$PI").
defenv SliceRef        :- "$SA:sliceGuid".
defenv SliceSet        :- computeIdFromName($SA,  "delegate/$User1/?SliceRef").
defenv TrustStructure  :- computeIdFromName($Self, 'subject/trust-structure').
defenv RegisteredUser  :- computeIdFromName($Self, 'subject/registered-user').
defenv GeniEndorsement :- computeIdFromName($GeniRoot, 'endorseAll').
defenv CPPolicySet     :- computeIdFromName($Self, 'object/aggregate-policy-slice-operations').

defetch fetchAllLinks() :- 
  [?EndorsePISet, ?EndorseUserSet, ?ProjectSet, ?SliceSet, ?TrustStructure, ?RegisteredUser, ?GeniEndorsement].

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

defguard authorize() :-
  spec('authorize the subject to access the slice on given project'),
  ?Subject        := "$User1",
  ?FetchedSets    := fetchAllLinks(),
  {
     importAll($FetchedSets).
     //import($CPPolicySet). // TODO

     sliverInstantiate(?Slice, ?Subject, ?Project) :-
       ?SA := rootId(?Slice),
       //println('printing SA'),
       //println(?SA),
       ?SA: slice(?Slice, ?Project, standard),
       //println('printing Slice'),
       //println(?Slice),
       sliceAuthority(?SA), 
       ?SA: controlPrivilege(?Subject, ?Slice, instantiate, _),
       geniUser(?Subject).

     geniRoot($GeniRoot).
 
     sliverInstantiate($SliceRef, $Subject, $ProjectRef)?
  }
end
