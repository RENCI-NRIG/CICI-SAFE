defenv SetVersion() :- "set12".
defenv PolicyVersion() :- "policy6".
defenv RpkiRoot() :- "iMrcWFMgx6DJeLtVWvBCMzwd8EDtJtZ4L0n3YYn1hi8".

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

defcon aclPolicy():-
  spec('example acl policy for customers'),
  {
    allow(?Bob):-
      endorsePA(?PA),
      ?PA: endorse(?Bob).
    label("acl policy($PolicyVersion)").
  }.

defpost postACLPolicy():- [aclPolicy()].

defcon connectivityPolicy():-
  spec ('carrier connectivity policy($PolicyVersion)'),
  {
    connect(?Alice,?Bob,?IPa, ?IPb):-
      $RpkiRoot: allocate(?Alice,?IPa),
      $RpkiRoot: allocate(?Bob,?IPb),
      ?Alice: allow(?Bob),
      ?Bob: allow(?Alice).
    label("carrier connectivity policy($PolicyVersion)").
  }.

defpost postConnectivityPolicy():-[connectivityPolicy()].


defcon ipAllocate(?Prcpl,?Prefix) :-
  {
    allocate($Prcpl,$Prefix).
    label("IP allocation($SetVersion)").
  }.

defpost postIPAllocate(?Prcpl,?Prefix) :-[ipAllocate(?Prcpl,?Prefix)].

defcon ownPrefixPolicy():-
  spec('own prefix policy'),
  {
    ownPrefix(?Customer,?IP):-
      $RpkiRoot: allocate(?Customer,?IP).

    label("own prefix policy($PolicyVersion)").
  }.

defpost postOwnPrefixPolicy() :- [ownPrefixPolicy()].

defguard ownPrefix(?Customer,?IP):-
  ?CustomerSubject  := label(?Customer,"subject($SetVersion)"),
  ?OwnPrefixPolicy := label("own prefix policy($PolicyVersion)"),
  {
    link($CustomerSubject).
    link($OwnPrefixPolicy).
    ownPrefix($Customer,$IP)?
  }.

defguard connectivity(?Alice,?Bob,?IPa,?IPb):-
  ?AliceACLPolicy := label(?Alice,"acl policy($PolicyVersion)"),
  ?BobACLPolicy := label(?Bob,"acl policy($PolicyVersion)"),
  ?ConnectivityPolicy := label("carrier connectivity policy($PolicyVersion)"),
  ?AliceSubject  := label(?Alice,"subject($SetVersion)"),
  ?BobSubject  := label(?Bob,"subject($SetVersion)"),
  ?PAAlice := label(?Alice,"my project authorization($SetVersion)"),
  ?PABob := label(?Bob,"my project authorization($SetVersion)"),
  {
    link($AliceACLPolicy).
    link($BobACLPolicy).
    link($ConnectivityPolicy).
    link($AliceSubject).
    link($BobSubject).
    link($PAAlice).
    link($PABob).
    connect($Alice,$Bob,$IPa,$IPb)?
  }.

