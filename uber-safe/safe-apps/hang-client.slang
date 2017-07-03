//defenv Selfie() :-
//  spec('Load the principal keypair'),
//  principal('/home/home3/hangzhu/Downloads/safe/pem/client-ab.pem').

defcon makeIdentitySet(?CN) :-
  spec("Construct the identity set for self"),
  {
    principal($SelfKey) :- spec("identity set for $CN").
  }.

defpost postIdSet(?CN) :- [makeIdentitySet(?CN)].

defcon myFacts() :-
  {
    trusted('k6ECXnwQ3EViFsBeYOdvipy6Z_iCVeBlTYoUaWKAKWE').
    tag(a,tag0).
    tag(a,tag1).
    tag(a,tag2).
    tag(b,tag2).
    tag(b,tag3).
    tag(b,tag4).

    label('AB/myFacts').
  }.
definit myFacts().

defcon setTag(?Client_id,?Tag) :-
  {
    tag($Client_id,$Tag).
    label($Client_id/$Tag).
  }.
defpost postTag(?Client_id,?Tag) :- [setTag(?Client_id,?Tag)].

defcon myPolicy() :-
  {
    tag(?Client_id,?Tag) :- trusted(?Y), ?Y: tag(?Client_id,?Tag).
    runs(?Client_id,?App) :- trusted(?Y), ?Y: runs(?Client_id,App).
    trusted_app(?App) :- trusted(?Y), ?Y: trusted_app(?App).
    trusted_instance(?Client_id,?Prefix,?App) :- trusted(?Y), ?Y: hasPrefix(?Client_id,?Prefix), runs(?Client_id,?App), trusted_app(?App).
    access(?Client_id,?A,?B,?Prefix,?App) :- tag(?A,?Tag),tag(?B,?Tag),tag(?Client_id,?Tag),trusted_instance(?Client_id,?Prefix,?App).
    label('AB/myPolicy').
  }.
definit myPolicy(). 
defguard testlist(?A) :-
 {
   member($A,[])?
 }.
defguard test(?A,?B) :-
  {
    edge(a,b,3).
    edge(b,c,1).
    edge(c,d,1).
    edge(b,a,3).
    edge(c,b,1).
    edge(d,c,1).
    path(X,Y) :- path(X,Y,[]).
    path(X,Y,V) :- \+(member(X,V)),edge(X,Y,W),traceln(Y),traceln(V).
    path(X,Y,V) :- \+(member(X,V)), edge(X,Z,W),traceln(Z),Z \==(Y),path(Z,Y,[X|V]), X \==(Y).
    path($A,$B)?
  }.

defguard testList(?A,?B, ?C, ?D) :-
  {
    approveAccess(?InstanceID, ?ObjectID, ?AccessForBundle) :-
      trustedCloudProvider(?InstanceID).
    trustedCloudProvider("cc").

    attestationChain(?InstanceID, []) :-
      trustedCloudProvider(?InstanceID).

    hostAttestsGuest("vm", "cc", "vm_hash").

    hostAttestsGuest("container", "vm", "container_hash").

    hostAttestsGuest("app", "container", "app_hash").

    attestationChain(?InstanceID, [?GuestProgramHash | ?HostChain]) :-
      hostAttestsGuest(?InstanceID, ?Host, ?GuestProgramHash),
      attestationChain(?Host, ?HostChain).

    // The same rule
    //attestationChain(?InstanceID, ?Chain) :-
    //  hostAttestsGuest(?InstanceID, ?Host, ?GuestProgramHash),
    //  attestationChain(?Host, ?HostChain),
    //  ?Chain = [?GuestProgramHash | ?HostChain].

    //attestationChain("app", ?Chain)?

    programHasAccessPrivilege(?ProgramHash, ?ObjectID, ?SkipCheck, ?AccessForBundle) :-
      ?SkipCheck = true,
      ?AccessForBundle = true.

    programHasAccessPrivilege("phash", "obj", $C, ?AccessForBundle)?

    //attestationChain("cc", ?Chain)?
    //approveAccess($A, $B, $C)?
  }.

defguard is_access(?Client_id,?A,?B,?Prefix,?App) :-
  ?ABPolicy := label('AB/myPolicy'),
  ?ABFacts := label('AB/myFacts'),
  ?SafeSet := label('k6ECXnwQ3EViFsBeYOdvipy6Z_iCVeBlTYoUaWKAKWE','safeset/myFacts'),
  {
    link($ABPolicy).
    link($ABFacts).
    link($SafeSet).
    trusted_instance($Client_id,$Prefix,$App)?
    access($Client_id,$A,$B,$Prefix,$App)?
  }.
