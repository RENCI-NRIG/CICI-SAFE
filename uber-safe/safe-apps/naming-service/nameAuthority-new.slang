defenv Selfie() :-
  spec('Load the principal keypair'),
  principal($1).
  (* principal('/home/qiang/Desktop/safe-new-version-benchmark/safe/safe-apps/naming-service/keys/na1.pem'). *)

(**
 *
 * Object records:
 *   nameObject(?Name, ?Scid, ?MountScid)
 *
 *)

defcon makeMyIdentitySet(?CN) :-
  spec('Construct an identity set for self'),
  {
    principal($SelfKey) :- spec("identity set for $CN").
  }.

defcon makeObjectSet(?Guid) :-
  spec("Construct an identity set for object ?Guid"),
  {
    label("$Guid").
  }.

defcon mountObject(?ObjectName, ?Scid, ?MountScid) :-
  spec('Mount an object'),
  ?MountGuid := objectFromScid(?MountScid),
  ?MountToken := label(?MountGuid),  
  {
    link($MountToken).
    nameObject($ObjectName, $Scid, $MountScid).
    label("$MountGuid/$ObjectName").
  }.

defcon updateObjectSet(?Token, ?Guid) :-
  spec("Add a link to an object set"),
  {
    link($Token).
    label("$Guid").
  }.

defpost postObjectDelegation(?ObjectName, ?Scid, ?MountScid) :- [mountObject(?ObjectName, ?Scid, ?MountScid)].

defpost postObjectSet(?Guid) :- [makeObjectSet(?Guid)]. 

defpost postIdSet(?CN) :- [makeMyIdentitySet(?CN)].

defpost postObjectSetUpdate(?Token, ?Guid) :- [updateObjectSet(?Token, ?Guid)].
