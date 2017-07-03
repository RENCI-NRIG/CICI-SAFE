defenv Selfie() :-
  spec('Load the principal keypair'),
  principal($1).
  (* principal('/home/qiang/Desktop/safe-new-version-benchmark/safe/safe-apps/naming-service/keys/na1.pem'). *)

(**
 *
 * Object records:
 *   O(?Name, ?Scid, ?MountScid)
 *
 *)

defcon makeMyIdentitySet(?CN) :-
  spec('Construct an identity set for self'),
  {
    principal($SelfKey) :- spec("identity set for $CN").
  }.

defcon mountObject(?ObjectName, ?Scid, ?MountScid) :-
  spec('Mount an object'),
  ?MountGuid := objectFromScid(?MountScid),
  {
    O($ObjectName, $Scid, $MountScid).
    name("$MountGuid:$ObjectName").
  }.

defcon addLink2ObjectSet(?LinkRef, ?ObjectName, ?MountScid) :-
  spec("Add a link to an object set"),
  ?MountGuid := objectFromScid(?MountScid),
  {
    link($LinkRef).
    name("$MountGuid:$ObjectName").
  }.

defpost postObject(?ObjectName, ?Scid, ?MountScid) :- [mountObject(?ObjectName, ?Scid, ?MountScid)].

defpost postIdSet(?CN) :- [makeMyIdentitySet(?CN)].

defpost postLink2ObjectSet(?LinkRef, ?ObjectName, ?MountScid) :- [addLink2ObjectSet(?LinkRef, ?ObjectName, ?MountScid)].
