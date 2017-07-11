
defenv Selfie :-
  spec('Load the principal keypair'),
  principal($1).

defcon makeMyIdSet(?CN) :-
  spec('Construct identity set for self'),
  {
    principal($SelfKey) :- spec("identity set for $CN").
  }.

defcon trustStructure(?CN) :-
  spec('Local policy for trust coordinators'),
  {
    //spec("Local policy for trust coordinators as defined by $CN").
    identityProvider(?IdP) :- geniRoot(?Geni), ?Geni: identityProvider(?IdP).
    sliceAuthority(?SA) :- geniRoot(?Geni), ?Geni: sliceAuthority(?SA).
    projectAuthority(?PA) :- geniRoot(?Geni), ?Geni: projectAuthority(?PA).
    geniProxy(?GeniProxy) :- geniRoot(?Geni), ?Geni: geniProxy(?GeniProxy).
    aggregate(?Agg) :- geniRoot(?Geni), ?Geni: aggregate(?Agg).
    gmoc(?GeniMOC) :- geniRoot(?Geni), ?Geni: gmoc(?GeniMOC).
    name('subject/trust-structure').
  }.

defcon registeredUserPolicy(?CN) :-
  spec('Local policy for validating GENI users and PIs'),
  {
    //spec("Local policy for validating GENI users and PIs as defined by $CN").
    geniUser(?User) :- identityProvider(?IdP), ?IdP: geniUser(?User).
    geniPI(?User) :- identityProvider(?IdP), ?IdP: geniPI(?User).
    name('subject/registered-user').
  }.

defpost bootstrap(?CN) :- [makeMyIdSet(?CN), trustStructure(?CN), registeredUserPolicy(?CN)].

definit bootstrap($2).
