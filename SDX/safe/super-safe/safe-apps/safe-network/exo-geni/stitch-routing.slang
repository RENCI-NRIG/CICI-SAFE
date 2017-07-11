defenv SetVersion() :- "set29".
defenv PolicyVersion() :- "policy23".
defenv RpkiRoot() :- "UIz4bXT7accigZ7KNpEyF2igwGOgXb9gne7p13i2bWA".

defcon makeMyIdentitySet(?CN) :-
  spec("Construct the identity set for self"),
  {
    principal($SelfKey) :- spec("identity set for $CN").
  }.

defpost postIdSet(?CN) :- [makeMyIdentitySet(?CN)].

defcon makeMySubjectSet() :-
  spec("Construct the subject set for self"),
  {
    link($Self).
    label("subject($SetVersion)").
  }.

defcon addTokenToSubjectSet(?Token) :-
  spec("Add a token to the user's subject set. Invoke after the user gets a delegation"),
  {
    link($Token).
    label("subject($SetVersion)").
  }.

defpost postSubjectSet() :- [makeMySubjectSet()].
defpost updateSubjectSet(?Token) :- [addTokenToSubjectSet(?Token)].

defcon stitchPolicy():-
  spec('carrier slice stitching policy'),
  {
    allowStitch(?Bob,?BobSlice,?ReservID,?Slice,?Node):-
      endorsePA(?PA),
      ?PA: endorse(?Bob),
      ?Bob: stitch(?BobSlice,?ReservID, ?Slice, ?Node).

    label("stitch policy($PolicyVersion)").
  }.

defpost postStitchPolicy() :- [stitchPolicy()].

defcon stitchRequest(?MySliceName,?ReserveID,?Slice,?Node):-
  spec('customer post a sttich request'),
  {
    stitch($MySliceName,$ReserveID,$Slice,$Node).
    label("stitch request($SetVersion)").
  }.

defpost postStitchRequest(?MySliceName,?ReservID,?Slice,?Node):-[stitchRequest(?MySliceName,?ReservID,?Slice,?Node)].

defcon endorsePM(?Member) :-
  spec('Endorse a project member'),
  {
    endorse($Member).
    label('endorse project member($SetVersion)').
  }.

defpost postEndorsePM(?Member) :- [endorsePM(?Member)].

defcon endorsePA(?PA) :-
  spec('carrierSlice states PA'),
  {
    endorsePA($PA).
    label("my project authorization($SetVersion)").
  }.

defpost postEndorsePA(?PA) :- [endorsePA(?PA)].

defguard verifyStitch(?Bob,?ClientSlice,?ReservID,?Slice,?Node):-
  ?StitchPolicy := label("stitch policy($PolicyVersion)"),
  ?PASet := label("my project authorization($SetVersion)"),
  ?SubjectToken := label(?Bob,"subject($SetVersion)"),
  ?RequestToken := label(?Bob,"stitch request($SetVersion)"),
  {
    link($StitchPolicy).
    link($PASet).
    link($SubjectToken).
    link($RequestToken).
    allowStitch($Bob,$ClientSlice,$ReservID,$Slice,$Node)?
  }.

defcon routingPolicy() :-
  {
    route(?DstIP,?Path) :-
      authorizedAdvertise(?Neighbor,?DstIPSet,?Path,$Self),
      ?DstIP <: ?DstIPSet.

    authorizedAdvertise(?Neighbor,?DstIP,?Path,?AS):-
      ?Neighbor: advertise(?DstIP,?Path,?AS),
      ownPrefix(?Neighbor,?DstIPSet),
      ?DstIP <: ?DstIPSet.

    authorizedAdvertise(?Neighbor,?DstIP,?Path,?AS):-
      eq([?Neighbor|?Tail],?Path),
      ?Neighbor:advertise(?DstIP,?Path,?AS),
      authorizedAdvertise(?P,?DstIPSet,?Tail,?Neighbor),
      ?DstIP <: ?DstIPSet.

    label("routing policy($PolicyVersion)").
  }.

defpost postRoutingPolicy() :- [routingPolicy()].

defcon ownPrefixPolicy():-
  {
    ownPrefix(?Prcpl,?Prefix):-
      $RpkiRoot: allocate(?Prcpl,?Prefix).

    ownPrefix(?Prcpl,?Prefix):-
      ?UpStream: allocate(?Prcpl,?Prefix),
      ownPrefix(?UpStream,?PrefixSet),
      ?Prefix <: ?PrefixSet.
    label("own prefix policy($PolicyVersion)").
  }.

defpost postOwnPrefixPolicy() :- [ownPrefixPolicy()].


defcon advertiseRoute(?DstIP,?Path,?Target,?Src,?Len):-
  ?PathToken := label("Path/?Src/?DstIP/?Len ($SetVersion)"),
  {
    link($PathToken).
    advertise($DstIP,$Path,$Target).
    label("Advertise/$Target/$DstIP/$Len ($SetVersion)").
  }.

defpost postAdvertise(?DstIP,?Path,?Target,?Src,?Len):- [advertiseRoute(?DstIP,?Path,?Target,?Src,?Len)].

defcon startRoute(?DstIP,?Path,?Target,?Len):-
  ?Token := label("Token for IP allocation/?DstIP($SetVersion)"),
  {
    link($Token).
    advertise($DstIP,$Path,$Target).
    label("Advertise/$Target/$DstIP/$Len ($SetVersion)").
  }.

defpost postInitRoute(?DstIP,?Path,?Target,?Len) :- [startRoute(?DstIP,?Path,?Target,?Len)].

defcon linkRoute(?Token,?DstIP,?Src,?Len):-
  spec("link a token for an advertised path"),
  {
    link($Token).
    label("Path/$Src/$DstIP/$Len ($SetVersion)").
  }.



defpost postPathToken(?Token,?DstIP,?Src,?Len) :- [linkRoute(?Token,?DstIP,?Src,?Len)].

//Could creat a seperate Set for each principal

defcon ipAllocate(?Prcpl,?Prefix,?DlgPrefix) :-
  ?IPSetToken := label("Token for IP allocation/?DlgPrefix($SetVersion)"),
  {
    link($IPSetToken).
    allocate($Prcpl,$Prefix).
    label("IP allocation/$Prcpl($SetVersion)").
  }.

defpost postIPAllocate(?Prcpl,?Prefix,?DlgPrefix) :-[ipAllocate(?Prcpl,?Prefix,?DlgPrefix)].

defcon addDlgToken(?Token,?Prefix):-
  spec("link a token for allocated IP range"),
  {
    link($Token).
    label("Token for IP allocation/$Prefix($SetVersion)").
  }.

defpost postDlgToken(?Token,?Prefix) :- [addDlgToken(?Token,?Prefix)].

defcon makeIPTokenSet(?DlgPrefix) :-
  {
    label("Token for IP allocation/$DlgPrefix($SetVersion)").
  }.

defpost postMakeIPTokenSet(?DlgPrefix):- [makeIPTokenSet(?DlgPrefix)].

defguard verifyRoute(?DstIP,?Path,?Src,?Len) :-
  ?RoutingPolicyRef := label("routing policy($PolicyVersion)"),
  ?PathToken := label("Path/?Src/?DstIP/?Len ($SetVersion)"),
  ?OwnPrefixPolicy := label("own prefix policy($PolicyVersion)"),
  {
    link($OwnPrefixPolicy).
    link($RoutingPolicyRef).
    link($PathToken).
    route($DstIP,$Path)?
  }.
