defcall postIdSet(?CN, ?PreferredStores) :-
  {
    postIdSet($ServerJVM, $Envs, $CN, $PreferredStores).
  }.

defcall postIdSetRaw(?CN) :-
  {
    postIdSet($ServerJVM, $Envs, $CN).
  }.

defcall updateIDSet(?StoreAddr, ?Protocol, ?ServerID) :-
  {
     updateIDSet($ServerJVM, $Envs, $StoreAddr, $Protocol, $ServerID).
  }.

defcall postGroupSet(?Group) :-
  {
    postGroupSet($ServerJVM, $Envs, $Group).
  }.

defcall postGroupMember(?GroupId, ?SubjectId, ?Delegatable) :-
  {
    postGroupMember($ServerJVM, $Envs, $GroupId, $SubjectId, $Delegatable).
  }.

defcall postDirectoryAccess(?GroupId, ?Scid) :-
  {
    postDirectoryAccess($ServerJVM, $Envs, $GroupId, $Scid).
  }.

//defcall updateSubjectSet(?Token) :-
//  {
//    updateSubjectSet($ServerJVM, $Envs, $Token).
//  }.

defcall accessNamedObject(?SubjectId, ?Name) :-
  {
    accessNamedObject($ServerJVM, $Envs, $SubjectId, $Name).
  }.

defcall tagAccessTest(?U) :-
  {
    tagAccessTest($ServerJVM, $Envs, $U).
  }.

defcall postPolicyIPV4() :-
  {
    postPolicyIPV4($ServerJVM, $Envs).
  }.

defcall testPolicy() :-
  {
    testPolicy($ServerJVM, $Envs).
  }.

defcall testipv4(?IP) :-
  {
    testipv4($ServerJVM, $Envs, $IP).
  }.

defcall passList(?A, ?B) :-
  {
    passList($ServerJVM, $Envs, $A, $B).
  }.


//hangCall(?A, ?B, ?C) :- ?Token := tagAccessTestForAlice(?A, ?B, ?C), updateSubjectSet(...., ?Token).

defcall tagAccessTestForAlice(?U) :-
  {
    tagAccessTest($ServerJVM, $Envs, $U).
  }.


defcall postPolicy(?PolicyId) :-
  {
    postPolicy($ServerJVM, $Envs, $PolicyId).
  }.


defcall postSubjectSet() :-
  {
    postSubjectSet($ServerJVM, $Envs).
  }.

defcall postSetWithList(?Name, ?Arg) :-
  {
    postSetWithList($ServerJVM, $Envs, $Name, $Arg).
  }.

defcall updateSubjectSet(?Token) :-
  {
    updateSubjectSet($ServerJVM, $Envs, $Token).
  }.


defcall mymethod() :-
  {
    mymethod($ServerJVM, $Envs).
  }.


 allocateIPAll(?AS, ?IP, ?P) :-
   ?Token := postAllocateIP(?AS, ?IP), switchSelfTo(?P),
   updateSubjectSet(?Token).

defcall test(?IP,?FileA,?FileB) :-
  {
    do_inspection($ServerJVM,$Envs,$IP,$FileA,$FileB).
  }.

defcall whoami() :-
  {
    whoami($ServerJVM, $Envs).
  }.

defcall queryName(?Name) :-
  {
    queryName($ServerJVM, $Envs, $Name).
  }.

defcall postNameDelegation(?ObjectName, ?ChildScid, ?ParentScid) :-
  {
    postNameDelegation($ServerJVM, $Envs, $ObjectName, $ChildScid, $ParentScid).
  }.

delegateName(?ObjectName, ?SubdirPrincipalName, ?SubdirLocalName, ?DirPrincipalName, ?DirLocalName) :-
  switchSelfTo(?SubdirPrincipalName),
  ?SubScidInfo := queryLocalName(?SubdirLocalName),
  ?SubScid := parseAndGetNthArg(?SubScidInfo, 1),
  switchSelfTo(?DirPrincipalName),
  ?DirScidInfo := queryLocalName(?DirLocalName),
  ?MountScid := parseAndGetNthArg(?DirScidInfo, 1),
  ?Token := postNameDelegation(?ObjectName, ?SubScid, ?MountScid),
  switchSelfTo(?SubdirPrincipalName),
  updateObjectSet(?Token, ?SubScid).  


delegateName(?ObjectName, ?SubdirPrincipalName, ?SubdirLocalName, ?ParentScid) :-
  switchSelfTo(?SubdirPrincipalName),
  ?SubScidInfo := queryLocalName(?SubdirLocalName),
  ?SubScid := parseAndGetNthArg(?SubScidInfo, 1),
  ?DirPID := rootPrincipal(?ParentScid),
  switchSelfTo(?DirPID),
  ?Token := postNameDelegation(?ObjectName, ?SubScid, ?ParentScid),
  switchSelfTo(?SubdirPrincipalName),
  updateObjectSet(?Token, ?SubScid).


defcall updateObjectSet(?Token, ?Scid) :-
  {
    updateObjectSet($ServerJVM, $Envs, $Token, $Scid).
  }.

defcall postObjectSet(?Scid) :-
  {
    postObjectSet($ServerJVM, $Envs, $Scid).
  }.

defcall queryLocalName(?Name) :-
  {
    queryLocalName($ServerJVM, $Envs, $Name).
  }.

defcall postLocalObject(?Name, ?Uuid) :-
  {
    postLocalObject($ServerJVM, $Envs, $Name, $Uuid).
  }.

testAll(?IP, ?FileA, ?FileB) :- ?NameA := queryName(?FileA), ?ScidA := parseAndGetNthArg(?NameA, 1), ?NameB := queryName(?FileB), ?ScidB := parseAndGetNthArg(?NameB, 1), test(?IP, ?ScidA, ?ScidB).


//hangCall(?A, ?B, ?C) :- ?Token := tagAccessTestForAlice(?A, ?B, ?C), updateSubjectSet(...., ?Token).
