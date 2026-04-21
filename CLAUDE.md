# notetaker

An Android app for people to share to-do lists.

## Features

- Create checklist notes for tracking things to remember
- Notes are locally stored or shareable with someone else who has the app
    - You can see whom a note is shared with at the bottom of the note
    - If a note is shared with you, you get a notification
- Notes have customizable titles
- View the titles + previews of all notes on an overview page
- Checklist items can be dragged up and down to reorder
- Items can be dragged right and left to indent/dedent
    - Gestures must not be too close to the edge of the screen or it may conflict with the OS-level "back" gesture
- Items have a checkbox on the left that, if tapped, gets checked
    - Checked items move to their own section at the bottom of the note
    - Checked checkboxes can be tapped to uncheck and move the item back to the main section
    - Item order is preserved and idempotent of checking/unchecking
    - Checked item text is greyed out
- Items can be blank
- When editing an item, hit Enter to create a new item
- When editing a blank item, hit Backspace to delete it
- When editing an item, an X icon appears to the right, and tapping the X will delete the item
- Undo/redo buttons at the note level
- If item length is too long, it wraps around neatly and is centered up-and-down with respect to the checkbox
- URL detection with clickable links
- You can color the background of notes
    - Pick from a selection of nice colors
    - Colors are not shared if the note is shared
- Notes can be unshared
    - If unshared, a copy of the note is kept on both devices but is no longer synced
    - The person who didn't initiate the unshare gets a notification
    - If an unshared note is re-shared, a new copy shows up (it doesn't overwrite the first note)
- Can delete notes locally with confirmation
    - If a note is shared, you must unshare it first
- Can archive notes locally, which moves them to a different section and makes them read-only
    - If a note is shared, you must unshare it first
    - Can unarchive too

## Infrastructure/design

- Security-focused
- Does not need a running server; all storage/compute on the device side
- Neat, tidy interface
- Repo is well-tested and everything runs in CI

## Instructions for agents

- Test-driven development is preferred; first, create tests for a feature, and then work on it until the tests succeed
- Commit after every turn!
- Feel free to edit this file with things to remember
