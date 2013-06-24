/*
 * Copyright 2011-2013 Gregory Shrago
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.grammar.parser;

import com.intellij.lang.*;
import com.intellij.lang.impl.PsiBuilderAdapter;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringHash;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.Function;
import com.intellij.util.PairProcessor;
import com.intellij.util.containers.LimitedPool;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;

/**
 * @author gregsh
 */
@SuppressWarnings("StringEquality")
public class GeneratedParserUtilBase {

  private static final Logger LOG = Logger.getInstance("org.intellij.grammar.parser.GeneratedParserUtilBase");

  public static final IElementType DUMMY_BLOCK = new DummyBlockElementType();

  public interface Parser {
    boolean parse(PsiBuilder builder, int level);
  }

  public static final Parser TOKEN_ADVANCER = new Parser() {
    @Override
    public boolean parse(PsiBuilder builder, int level) {
      if (builder.eof()) return false;
      builder.advanceLexer();
      return true;
    }
  };

  public static final Parser TRUE_CONDITION = new Parser() {
    @Override
    public boolean parse(PsiBuilder builder, int level) {
      return true;
    }
  };

  public static boolean eof(PsiBuilder builder_, int level_) {
    return builder_.eof();
  }

  public static boolean recursion_guard_(PsiBuilder builder_, int level_, String funcName_) {
    if (level_ > 1000) {
      builder_.error("Maximum recursion level (" + 1000 + ") reached in " + funcName_);
      return false;
    }
    return true;
  }

  public static void empty_element_parsed_guard_(PsiBuilder builder_, int offset_, String funcName_) {
    builder_.error("Empty element parsed in " + funcName_ +" at offset " + offset_);
  }

  public static boolean invalid_left_marker_guard_(PsiBuilder builder_, PsiBuilder.Marker marker_, String funcName_) {
    //builder_.error("Invalid left marker encountered in " + funcName_ +" at offset " + builder_.getCurrentOffset());
    boolean goodMarker = marker_ != null; // && ((LighterASTNode)marker_).getTokenType() != TokenType.ERROR_ELEMENT;
    if (!goodMarker) return false;
    ErrorState state = ErrorState.get(builder_);

    Frame frame = state.frameStack.peekLast();
    return frame == null || frame.errorReportedAt <= builder_.getCurrentOffset();
  }

  public static TokenSet create_token_set_(IElementType... tokenTypes_) {
    return TokenSet.create(tokenTypes_);
  }

  public static boolean consumeTokens(PsiBuilder builder_, int pin_, IElementType... tokens_) {
    ErrorState state = ErrorState.get(builder_);
    if (state.completionState != null && state.predicateSign) {
      addCompletionVariant(builder_, state, state.completionState, tokens_, builder_.getCurrentOffset());
    }
    // suppress single token completion
    CompletionState completionState = state.completionState;
    state.completionState = null;
    boolean result_ = true;
    boolean pinned_ = false;
    for (int i = 0, tokensLength = tokens_.length; i < tokensLength; i++) {
      if (pin_ > 0 && i == pin_) pinned_ = result_;
      if ((result_ || pinned_) && !consumeToken(builder_, tokens_[i])) {
        result_ = false;
        if (pin_ < 0 || pinned_) report_error_(builder_, state, false);
      }
    }
    state.completionState = completionState;
    return pinned_ || result_;
  }

  public static boolean parseTokens(PsiBuilder builder_, int pin_, IElementType... tokens_) {
    PsiBuilder.Marker marker_ = builder_.mark();
    boolean result_ = consumeTokens(builder_, pin_, tokens_);
    if (!result_) {
      marker_.rollbackTo();
    }
    else {
      marker_.drop();
    }
    return result_;
  }

  public static boolean consumeToken(PsiBuilder builder_, IElementType token) {
    if (nextTokenIsInner(builder_, token, true)) {
      builder_.advanceLexer();
      return true;
    }
    return false;
  }

  public static boolean consumeTokenFast(PsiBuilder builder_, IElementType token) {
    if (builder_.getTokenType() == token) {
      builder_.advanceLexer();
      return true;
    }
    return false;
  }

  public static boolean consumeTokenFast(PsiBuilder builder_, String text) {
    if (Comparing.strEqual(builder_.getTokenText(), text, ErrorState.get(builder_).caseSensitive)) {
      builder_.advanceLexer();
      return true;
    }
    return false;
  }

  public static boolean nextTokenIsFast(PsiBuilder builder_, IElementType token) {
    return builder_.getTokenType() == token;
  }

  public static boolean nextTokenIs(PsiBuilder builder_, IElementType token) {
    return nextTokenIsInner(builder_, token, false);
  }

  public static boolean nextTokenIsInner(PsiBuilder builder_, IElementType token, boolean force) {
    ErrorState state = ErrorState.get(builder_);
    if (state.completionState != null && !force) return true;
    IElementType tokenType = builder_.getTokenType();
    if (!state.suppressErrors && state.predicateCount < 2) {
      addVariant(builder_, state, token);
    }
    return token == tokenType;
  }

  public static boolean replaceVariants(PsiBuilder builder_, int variantCount, String frameName) {
    ErrorState state = ErrorState.get(builder_);
    if (!state.suppressErrors && state.predicateCount < 2 && state.predicateSign) {
      state.clearVariants(true, state.variants.size() - variantCount);
      addVariantInner(state, builder_.getCurrentOffset(), frameName);
    }
    return true;
  }

  public static void addVariant(PsiBuilder builder_, String text) {
    addVariant(builder_, ErrorState.get(builder_), text);
  }

  private static void addVariant(PsiBuilder builder_, ErrorState state, Object o) {
    int offset = builder_.getCurrentOffset();
    addVariantInner(state, offset, o);

    CompletionState completionState = state.completionState;
    if (completionState != null && state.predicateSign) {
      addCompletionVariant(builder_, state, completionState, o, offset);
    }
  }

  private static void addVariantInner(ErrorState state, int offset, Object o) {
    Variant variant = state.VARIANTS.alloc().init(offset, o);
    if (state.predicateSign) {
      state.variants.add(variant);
      if (state.lastExpectedVariantOffset < variant.offset) {
        state.lastExpectedVariantOffset = variant.offset;
      }
    }
    else {
      state.unexpected.add(variant);
    }
  }

  public static boolean consumeToken(PsiBuilder builder_, String text) {
    ErrorState state = ErrorState.get(builder_);
    if (!state.suppressErrors && state.predicateCount < 2) {
      addVariant(builder_, state, text);
    }
    return consumeTokenInner(builder_, text, state.caseSensitive);
  }

  public static boolean consumeTokenInner(PsiBuilder builder_, String text, boolean caseSensitive) {
    final CharSequence sequence = builder_.getOriginalText();
    final int offset = builder_.getCurrentOffset();
    final int endOffset = offset + text.length();
    CharSequence tokenText = sequence.subSequence(offset, Math.min(endOffset, sequence.length()));

    if (Comparing.equal(text, tokenText, caseSensitive)) {
      int count = 0;
      while (true) {
        final int nextOffset = builder_.rawTokenTypeStart(++ count);
        if (nextOffset > endOffset) {
          return false;
        }
        else if (nextOffset == endOffset) {
          break;
        }
      }
      while (count-- > 0) builder_.advanceLexer();
      return true;
    }
    return false;
  }

  private static void addCompletionVariant(PsiBuilder builder_,
                                           ErrorState state,
                                           CompletionState completionState,
                                           Object o,
                                           int offset) {
    boolean add = false;
    int diff = completionState.offset - offset;
    String text = completionState.convertItem(o);
    int length = text == null? 0 : text.length();
    if (length == 0) return;
    if (diff == 0) {
      add = true;
    }
    else if (diff > 0 && diff <= length) {
      CharSequence fragment = builder_.getOriginalText().subSequence(offset, completionState.offset);
      add = StringUtil.startsWithIgnoreCase(text, fragment.toString());
    }
    else if (diff < 0) {
      for (int i=-1; ; i--) {
        IElementType type = builder_.rawLookup(i);
        int tokenStart = builder_.rawTokenTypeStart(i);
        if (((PsiBuilderImpl)((Builder)builder_).getDelegate()).whitespaceOrComment(type)) {
          diff = completionState.offset - tokenStart;
        }
        else if (type != null && tokenStart < completionState.offset) {
          CharSequence fragment = builder_.getOriginalText().subSequence(tokenStart, completionState.offset);
          if (StringUtil.startsWithIgnoreCase(text, fragment.toString())) {
            diff = completionState.offset - tokenStart;
          }
          break;
        }
        else break;
      }
      add = diff >= 0 && diff < length;
    }
    add = add && length > 1 && !(text.charAt(0) == '<' && text.charAt(length - 1) == '>') &&
          !(text.charAt(0) == '\'' && text.charAt(length - 1) == '\'' && length < 5);
    if (add) {
      completionState.items.add(text);
    }
  }

  // keep the old section API for compatibility
  public static final String _SECTION_NOT_ = "_SECTION_NOT_";
  public static final String _SECTION_AND_ = "_SECTION_AND_";
  public static final String _SECTION_RECOVER_ = "_SECTION_RECOVER_";
  public static final String _SECTION_GENERAL_ = "_SECTION_GENERAL_";

  public static void enterErrorRecordingSection(PsiBuilder builder_, int level, @NotNull String sectionType, @Nullable String frameName) {
    int modifiers = sectionType == _SECTION_GENERAL_ ? _NONE_ :
                    sectionType == _SECTION_NOT_ ? _NOT_ :
                    sectionType == _SECTION_AND_ ? _AND_ : _NONE_;
    enter_section_impl_(builder_, level, modifiers, frameName);
  }

  public static boolean exitErrorRecordingSection(PsiBuilder builder_,
                                                  int level,
                                                  boolean result,
                                                  boolean pinned,
                                                  @NotNull String sectionType,
                                                  @Nullable Parser eatMore) {
    exit_section_(builder_, level, null, null, result, pinned, eatMore);
    return result;
  }

  // here's the new section API for compact parsers & less IntelliJ platform API exposure
  public static final int _NONE_       = 0x0;
  public static final int _COLLAPSE_   = 0x1;
  public static final int _LEFT_       = 0x2;
  public static final int _LEFT_INNER_ = 0x4;
  public static final int _AND_        = 0x8;
  public static final int _NOT_        = 0x10;

  // simple enter/exit methods pair that doesn't require frame object
  public static PsiBuilder.Marker enter_section_(PsiBuilder builder_) {
    return builder_.mark();
  }

  public static void exit_section_(PsiBuilder builder_,
                                   PsiBuilder.Marker marker,
                                   @Nullable IElementType elementType,
                                   boolean result) {
    close_marker_impl_(ErrorState.get(builder_).frameStack.peekLast(), marker, elementType, result);
  }

  // complex enter/exit methods pair with frame object
  public static PsiBuilder.Marker enter_section_(PsiBuilder builder_, int level, int modifiers, @Nullable String frameName) {
    PsiBuilder.Marker marker = builder_.mark();
    enter_section_impl_(builder_, level, modifiers, frameName);
    return marker;
  }

  private static void enter_section_impl_(PsiBuilder builder_, int level, int modifiers, @Nullable String frameName) {
    ErrorState state = ErrorState.get(builder_);
    Frame frame = state.FRAMES.alloc().init(builder_.getCurrentOffset(), level, modifiers, frameName, state);
    if (((frame.modifiers & _LEFT_) | (frame.modifiers & _LEFT_INNER_)) != 0) {
      PsiBuilder.Marker left = (PsiBuilder.Marker)builder_.getLatestDoneMarker();
      if (invalid_left_marker_guard_(builder_, left, frameName)) {
        frame.leftMarker = left;
      }
    }
    state.frameStack.add(frame);
    if ((modifiers & _AND_) != 0) {
      if (state.predicateCount == 0 && !state.predicateSign) {
        throw new AssertionError("Incorrect false predicate sign");
      }
      state.predicateCount++;
    }
    else if ((modifiers & _NOT_) != 0) {
      if (state.predicateCount == 0) {
        state.predicateSign = false;
      }
      else {
        state.predicateSign = !state.predicateSign;
      }
      state.predicateCount++;
    }
  }

  public static void exit_section_(PsiBuilder builder_,
                                   int level,
                                   PsiBuilder.Marker marker,
                                   @Nullable IElementType elementType,
                                   boolean result,
                                   boolean pinned,
                                   @Nullable Parser eatMore) {
    ErrorState state = ErrorState.get(builder_);

    Frame frame = state.frameStack.pollLast();
    if (frame == null || level != frame.level) {
      LOG.error("Unbalanced error section: got " + frame + ", expected level " + level);
      if (frame != null) state.FRAMES.recycle(frame);
      close_marker_impl_(frame, marker, elementType, result);
      return;
    }

    if (((frame.modifiers & _AND_) | (frame.modifiers & _NOT_)) != 0) {
      close_marker_impl_(frame, marker, null, false);
      state.predicateCount--;
      if ((frame.modifiers & _NOT_) != 0) state.predicateSign = !state.predicateSign;
      state.FRAMES.recycle(frame);
      return;
    }
    exit_section_impl_(state, frame, builder_, marker, elementType, result, pinned);

    int initialOffset = builder_.getCurrentOffset();
    boolean willFail = !result && !pinned;
    if (willFail && initialOffset == frame.offset && state.lastExpectedVariantOffset == frame.offset &&
        frame.name != null && state.variants.size() - frame.variantCount > 1) {
      state.clearVariants(true, frame.variantCount);
      addVariantInner(state, initialOffset, frame.name);
    }
    if (!state.suppressErrors && eatMore != null) {
      state.suppressErrors = true;
      final boolean eatMoreFlagOnce = !builder_.eof() && eatMore.parse(builder_, frame.level + 1);
      final int lastErrorPos = getLastVariantOffset(state, initialOffset);
      boolean eatMoreFlag = eatMoreFlagOnce || !result && frame.offset == initialOffset && lastErrorPos > frame.offset;

      final LighterASTNode latestDoneMarker =
        (pinned || result) && (state.altMode || lastErrorPos > initialOffset || level == 0) &&
        eatMoreFlagOnce ? builder_.getLatestDoneMarker() : null;
      PsiBuilder.Marker extensionMarker = null;
      IElementType extensionTokenType = null;
      // whitespace prefix makes the very first frame offset bigger than marker start offset which is always 0
      if (latestDoneMarker instanceof PsiBuilder.Marker && frame.offset >= latestDoneMarker.getStartOffset()) {
        extensionMarker = ((PsiBuilder.Marker)latestDoneMarker).precede();
        extensionTokenType = latestDoneMarker.getTokenType();
        ((PsiBuilder.Marker)latestDoneMarker).drop();
      }
      // advance to the last error pos
      // skip tokens until lastErrorPos. parseAsTree might look better here...
      int parenCount = 0;
      while ((eatMoreFlag || parenCount > 0) && builder_.getCurrentOffset() < lastErrorPos) {
        if (state.braces != null) {
          if (builder_.getTokenType() == state.braces[0].getLeftBraceType()) parenCount ++;
          else if (builder_.getTokenType() == state.braces[0].getRightBraceType()) parenCount --;
        }
        builder_.advanceLexer();
        eatMoreFlag = eatMore.parse(builder_, frame.level + 1);
      }
      boolean errorReported = frame.errorReportedAt == initialOffset || !result && frame.errorReportedAt >= frame.offset;
      if (errorReported) {
        if (eatMoreFlag) {
          builder_.advanceLexer();
          parseAsTree(state, builder_, frame.level + 1, DUMMY_BLOCK, true, TOKEN_ADVANCER, eatMore);
        }
      }
      else if (eatMoreFlag) {
        errorReported = reportError(builder_, state, frame, true, true);
        parseAsTree(state, builder_, frame.level + 1, DUMMY_BLOCK, true, TOKEN_ADVANCER, eatMore);
      }
      else if (eatMoreFlagOnce || (!result && frame.offset != builder_.getCurrentOffset())) {
        errorReported = reportError(builder_, state, frame, true, false);
      }
      if (extensionMarker != null) {
        extensionMarker.done(extensionTokenType);
      }
      state.suppressErrors = false;
      if (errorReported || result) {
        state.clearVariants(true, 0);
        state.clearVariants(false, 0);
        state.lastExpectedVariantOffset = -1;
      }
    }
    else if (!result && pinned && frame.errorReportedAt < 0) {
      // do not report if there're errors after current offset
      if (getLastVariantOffset(state, initialOffset) == initialOffset) {
        // do not force, inner recoverRoot might have skipped some tokens
        reportError(builder_, state, frame, false, false);
      }
    }
    // propagate errorReportedAt up the stack to avoid duplicate reporting
    Frame prevFrame = willFail && eatMore == null ? null : state.frameStack.peekLast();
    if (prevFrame != null && prevFrame.errorReportedAt < frame.errorReportedAt) prevFrame.errorReportedAt = frame.errorReportedAt;
    state.FRAMES.recycle(frame);
  }

  private static void exit_section_impl_(ErrorState state,
                                         Frame frame,
                                         PsiBuilder builder_,
                                         PsiBuilder.Marker marker,
                                         IElementType elementType,
                                         boolean result,
                                         boolean pinned) {
    if (elementType != null && marker != null) {
      if ((frame.modifiers & _COLLAPSE_) != 0) {
        LighterASTNode last = result || pinned? builder_.getLatestDoneMarker() : null;
        if (last != null && last.getStartOffset() == frame.offset && state.typeExtends(last.getTokenType(), elementType)) {
          marker.drop();
          return;
        }
      }
      if (result || pinned) {
        if ((frame.modifiers & _LEFT_INNER_) != 0 && frame.leftMarker != null) {
          marker.done(elementType);
          frame.leftMarker.precede().done(((LighterASTNode)frame.leftMarker).getTokenType());
          frame.leftMarker.drop();
        }
        else if ((frame.modifiers & _LEFT_) != 0 && frame.leftMarker != null) {
          marker.drop();
          frame.leftMarker.precede().done(elementType);
        }
        else {
          marker.done(elementType);
        }
      }
      else {
        close_marker_impl_(frame, marker, null, false);
      }
    }
    else if (result || pinned) {
      if (marker != null) marker.drop();
      if ((frame.modifiers & _LEFT_INNER_) != 0 && frame.leftMarker != null) {
        frame.leftMarker.precede().done(((LighterASTNode)frame.leftMarker).getTokenType());
        frame.leftMarker.drop();
      }
    }
    else {
      close_marker_impl_(frame, marker, null, false);
    }
  }

  private static void close_marker_impl_(Frame frame, PsiBuilder.Marker marker, IElementType elementType, boolean result) {
    if (marker == null) return;
    if (result) {
      if (elementType != null) {
        marker.done(elementType);
      }
      else {
        marker.drop();
      }
    }
    else {
      if (frame != null) {
        int offset = ((LighterASTNode)marker).getStartOffset();
        if (frame.errorReportedAt > offset) {
          frame.errorReportedAt = frame.errorReportedAtPrev;
        }
      }
      marker.rollbackTo();
    }
  }

  public static boolean report_error_(PsiBuilder builder_, boolean result_) {
    if (!result_) report_error_(builder_, ErrorState.get(builder_), false);
    return result_;
  }

  public static void report_error_(PsiBuilder builder_, ErrorState state, boolean advance) {
    Frame frame = state.frameStack.isEmpty()? null : state.frameStack.getLast();
    if (frame == null) {
      LOG.error("Unbalanced error section: got null , expected " + frame);
      return;
    }
    int offset = builder_.getCurrentOffset();
    if (frame.errorReportedAt < offset && getLastVariantOffset(state, builder_.getCurrentOffset()) <= offset) {
      reportError(builder_, state, frame, true, advance);
    }
  }

  private static int getLastVariantOffset(ErrorState state, int defValue) {
    return state.lastExpectedVariantOffset < 0? defValue : state.lastExpectedVariantOffset;
  }

  private static boolean reportError(PsiBuilder builder_,
                                     ErrorState state,
                                     Frame frame,
                                     boolean force,
                                     boolean advance) {
    String expectedText = state.getExpectedText(builder_);
    boolean notEmpty = StringUtil.isNotEmpty(expectedText);
    if (force || notEmpty || advance) {
      String gotText = builder_.eof()? "unexpected end of file" :
                             notEmpty? "got '" + builder_.getTokenText() +"'" :
                             "'" + builder_.getTokenText() +"' unexpected";
      String message = expectedText + gotText;
      if (advance) {
        PsiBuilder.Marker mark = builder_.mark();
        builder_.advanceLexer();
        mark.error(message);
      }
      else {
        builder_.error(message);
      }
      frame.errorReportedAt = builder_.getCurrentOffset();
      return true;
    }
    return false;
  }


  public static final Key<CompletionState> COMPLETION_STATE_KEY = Key.create("COMPLETION_STATE_KEY");

  public static class CompletionState implements Function<Object, String> {
    public final int offset;
    public final Collection<String> items = new THashSet<String>();

    public CompletionState(int offset) {
      this.offset = offset;
    }

    @Nullable
    public String convertItem(Object o) {
      return o instanceof Object[] ? StringUtil.join((Object[]) o, this, " ") : o.toString();
    }

    @Override
    public String fun(Object o) {
      return o.toString();
    }
  }

  public static class Builder extends PsiBuilderAdapter {
    public final ErrorState state;
    public final PsiParser parser;

    public Builder(PsiBuilder builder, ErrorState state, PsiParser parser) {
      super(builder);
      this.state = state;
      this.parser = parser;
    }

    public Lexer getLexer() {
      return ((PsiBuilderImpl)myDelegate).getLexer();
    }
  }

  public static PsiBuilder adapt_builder_(IElementType root, PsiBuilder builder, PsiParser parser) {
    return adapt_builder_(root, builder, parser, null);
  }

  public static PsiBuilder adapt_builder_(IElementType root, PsiBuilder builder, PsiParser parser, TokenSet[] extendsSets) {
    ErrorState state = new ErrorState();
    ErrorState.initState(state, builder, root, extendsSets);
    return new Builder(builder, state, parser);
  }

  public static class ErrorState {
    TokenSet[] extendsSets;
    public PairProcessor<IElementType, IElementType> altExtendsChecker;

    int predicateCount;
    boolean predicateSign = true;
    boolean suppressErrors;
    public final LinkedList<Frame> frameStack = new LinkedList<Frame>();
    public CompletionState completionState;

    private boolean caseSensitive;
    public BracePair[] braces;
    public boolean altMode;

    private int lastExpectedVariantOffset = -1;
    public MyList<Variant> variants = new MyList<Variant>(500);
    public MyList<Variant> unexpected = new MyList<Variant>(10);

    final LimitedPool<Variant> VARIANTS = new LimitedPool<Variant>(1000, new LimitedPool.ObjectFactory<Variant>() {
      @Override
      public Variant create() {
        return new Variant();
      }

      @Override
      public void cleanup(final Variant o) {
      }
    });
    final LimitedPool<Frame> FRAMES = new LimitedPool<Frame>(100, new LimitedPool.ObjectFactory<Frame>() {
      @Override
      public Frame create() {
        return new Frame();
      }

      @Override
      public void cleanup(final Frame o) {
      }
    });

    public static ErrorState get(PsiBuilder builder) {
      return ((Builder)builder).state;
    }

    private static void initState(ErrorState state, PsiBuilder builder, IElementType root, TokenSet[] extendsSets) {
      state.extendsSets = extendsSets;
      PsiFile file = builder.getUserDataUnprotected(FileContextUtil.CONTAINING_FILE_KEY);
      state.completionState = file == null? null: file.getUserData(COMPLETION_STATE_KEY);
      Language language = file == null? root.getLanguage() : file.getLanguage();
      state.caseSensitive = language.isCaseSensitive();
      PairedBraceMatcher matcher = LanguageBraceMatching.INSTANCE.forLanguage(language);
      state.braces = matcher == null ? null : matcher.getPairs();
      if (state.braces != null && state.braces.length == 0) state.braces = null;
    }

    public String getExpectedText(PsiBuilder builder_) {
      int offset = builder_.getCurrentOffset();
      StringBuilder sb = new StringBuilder();
      if (addExpected(sb, offset, true)) {
        sb.append(" expected, ");
      }
      else if (addExpected(sb, offset, false)) sb.append(" unexpected, ");
      return sb.toString();
    }

    private static final int MAX_VARIANTS_TO_DISPLAY = Integer.MAX_VALUE;
    private boolean addExpected(StringBuilder sb, int offset, boolean expected) {
      MyList<Variant> list = expected ? variants : unexpected;
      String[] strings = new String[list.size()];
      long[] hashes = new long[strings.length];
      Arrays.fill(strings, "");
      int count = 0;
      loop: for (Variant variant : list) {
        if (offset == variant.offset) {
          String text = variant.object.toString();
          long hash = StringHash.calc(text);
          for (int i=0; i<count; i++) {
            if (hashes[i] == hash) continue loop;
          }
          hashes[count] = hash;
          strings[count] = text;
          count++;
        }
      }
      Arrays.sort(strings);
      count = 0;
      for (String s : strings) {
        if (s.length() == 0) continue;
        if (count++ > 0) {
          if (count > MAX_VARIANTS_TO_DISPLAY) {
            sb.append(" and ...");
            break;
          }
          else {
            sb.append(", ");
          }
        }
        char c = s.charAt(0);
        String displayText = c == '<' || StringUtil.isJavaIdentifierStart(c) ? s : '\'' + s + '\'';
        sb.append(displayText);
      }
      if (count > 1 && count < MAX_VARIANTS_TO_DISPLAY) {
        int idx = sb.lastIndexOf(", ");
        sb.replace(idx, idx + 1, " or");
      }
      return count > 0;
    }

    public void clearVariants(boolean expected, int start) {
      MyList<Variant> list = expected? variants : unexpected;
      for (int i = start, len = list.size(); i < len; i ++) {
        VARIANTS.recycle(list.get(i));
      }
      list.setSize(start);
    }

    boolean typeExtends(IElementType child_, IElementType parent_) {
      if (extendsSets == null) {
        return child_ == parent_ ||
               altExtendsChecker != null && altExtendsChecker.process(child_, parent_);
      }
      for (TokenSet set : extendsSets) {
        if (set.contains(child_) && set.contains(parent_)) return true;
      }
      return false;
    }
  }

  public static class Frame {
    public int offset;
    public int level;
    public int modifiers;
    public String name;
    public int variantCount;
    public int errorReportedAt;
    public int errorReportedAtPrev;
    public PsiBuilder.Marker leftMarker;

    public Frame() {
    }

    public Frame init(int offset, int level, int modifiers, String name, ErrorState state) {
      this.offset = offset;
      this.level = level;
      this.modifiers = modifiers;
      this.name = name;
      this.variantCount = state.variants.size();
      this.errorReportedAt = -1;

      Frame prev = state.frameStack.peekLast();
      errorReportedAtPrev = prev == null? -1 : prev.errorReportedAt;
      leftMarker = null;
      return this;
    }

    @Override
    public String toString() {
      String mod = modifiers == _NONE_ ? "_NONE_, " :
        ((modifiers & _COLLAPSE_) != 0? "_CAN_COLLAPSE_, ": "") +
        ((modifiers & _LEFT_) != 0? "_LEFT_, ": "") +
        ((modifiers & _LEFT_INNER_) != 0? "_LEFT_INNER_, ": "") +
        ((modifiers & _AND_) != 0? "_AND_, ": "") +
        ((modifiers & _NOT_) != 0? "_NOT_, ": "");
      return "<" + offset + ", " + mod + level + (errorReportedAt > -1 ? ", [" + errorReportedAt + "]" : "") + ">";
    }
  }


  public static class Variant {
    int offset;
    Object object;

    public Variant init(int offset, Object text) {
      this.offset = offset;
      this.object = text;
      return this;
    }

    @Override
    public String toString() {
      return "<" + offset + ", " + object + ">";
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Variant variant = (Variant)o;

      if (offset != variant.offset) return false;
      if (!this.object.equals(variant.object)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = offset;
      result = 31 * result + object.hashCode();
      return result;
    }
  }

  @Nullable
  private static IElementType getClosingBracket(ErrorState state, IElementType type) {
    if (state.braces == null) return null;
    for (BracePair pair : state.braces) {
      if (type == pair.getLeftBraceType()) return pair.getRightBraceType();
    }
    return null;
  }


  private static final int MAX_CHILDREN_IN_TREE = 10;
  public static boolean parseAsTree(ErrorState state, final PsiBuilder builder_, int level, final IElementType chunkType,
                                    boolean checkBraces, final Parser parser, final Parser eatMoreCondition) {
    final LinkedList<Pair<PsiBuilder.Marker, PsiBuilder.Marker>> parenList = new LinkedList<Pair<PsiBuilder.Marker, PsiBuilder.Marker>>();
    final LinkedList<Pair<PsiBuilder.Marker, Integer>> siblingList = new LinkedList<Pair<PsiBuilder.Marker, Integer>>();
    PsiBuilder.Marker marker = null;

    final Runnable checkSiblingsRunnable = new Runnable() {
      @Override
      public void run() {
        main:
        while (!siblingList.isEmpty()) {
          final Pair<PsiBuilder.Marker, PsiBuilder.Marker> parenPair = parenList.peek();
          final int rating = siblingList.getFirst().second;
          int count = 0;
          for (Pair<PsiBuilder.Marker, Integer> pair : siblingList) {
            if (pair.second != rating || parenPair != null && pair.first == parenPair.second) break main;
            if (++count >= MAX_CHILDREN_IN_TREE) {
              final PsiBuilder.Marker parentMarker = pair.first.precede();
              while (count-- > 0) {
                siblingList.removeFirst();
              }
              parentMarker.done(chunkType);
              siblingList.addFirst(Pair.create(parentMarker, rating + 1));
              continue main;
            }
          }
          break;
        }
      }
    };
    boolean checkParens = state.braces != null && checkBraces;
    int totalCount = 0;
    int tokenCount = 0;
    if (checkParens) {
      int tokenIdx = -1;
      while (builder_.rawLookup(tokenIdx) == TokenType.WHITE_SPACE) tokenIdx --;
      LighterASTNode doneMarker = builder_.rawLookup(tokenIdx) == state.braces[0].getLeftBraceType() ? builder_.getLatestDoneMarker() : null;
      if (doneMarker != null && doneMarker.getStartOffset() == builder_.rawTokenTypeStart(tokenIdx) && doneMarker.getTokenType() == TokenType.ERROR_ELEMENT) {
        parenList.add(Pair.create(((PsiBuilder.Marker)doneMarker).precede(), (PsiBuilder.Marker)null));
      }
    }
    while (true) {
      final IElementType tokenType = builder_.getTokenType();
      if (checkParens && (tokenType == state.braces[0].getLeftBraceType() || tokenType == state.braces[0].getRightBraceType() && !parenList.isEmpty())) {
        if (marker != null) {
          marker.done(chunkType);
          siblingList.addFirst(Pair.create(marker, 1));
          marker = null;
          tokenCount = 0;
        }
        if (tokenType == state.braces[0].getLeftBraceType()) {
          final Pair<PsiBuilder.Marker, Integer> prev = siblingList.peek();
          parenList.addFirst(Pair.create(builder_.mark(), prev == null ? null : prev.first));
        }
        checkSiblingsRunnable.run();
        builder_.advanceLexer();
        if (tokenType == state.braces[0].getRightBraceType()) {
          final Pair<PsiBuilder.Marker, PsiBuilder.Marker> pair = parenList.removeFirst();
          pair.first.done(chunkType);
          // drop all markers inside parens
          while (!siblingList.isEmpty() && siblingList.getFirst().first != pair.second) {
            siblingList.removeFirst();
          }
          siblingList.addFirst(Pair.create(pair.first, 1));
          checkSiblingsRunnable.run();
        }
      }
      else {
        if (marker == null) {
          marker = builder_.mark();
        }
        final boolean result = (!parenList.isEmpty() || eatMoreCondition.parse(builder_, level + 1)) && parser.parse(builder_, level + 1);
        if (result) {
          tokenCount++;
          totalCount++;
        }
        if (!result) {
          break;
        }
      }

      if (tokenCount >= MAX_CHILDREN_IN_TREE && marker != null) {
        marker.done(chunkType);
        siblingList.addFirst(Pair.create(marker, 1));
        checkSiblingsRunnable.run();
        marker = null;
        tokenCount = 0;
      }
    }
    if (marker != null) {
      marker.drop();
    }
    for (Pair<PsiBuilder.Marker, PsiBuilder.Marker> pair : parenList) {
      pair.first.drop();
    }
    return totalCount != 0;
  }

  private static class DummyBlockElementType extends IElementType implements ICompositeElementType{
    DummyBlockElementType() {
      super("DUMMY_BLOCK", Language.ANY);
    }

    @NotNull
    @Override
    public ASTNode createCompositeNode() {
      return new DummyBlock();
    }
  }

  public static class DummyBlock extends CompositePsiElement {
    DummyBlock() {
      super(DUMMY_BLOCK);
    }

    @Override
    public PsiReference[] getReferences() {
      return PsiReference.EMPTY_ARRAY;
    }

    @NotNull
    @Override
    public Language getLanguage() {
      return getParent().getLanguage();
    }
  }

  protected static class MyList<E> extends ArrayList<E> {
    public MyList(int initialCapacity) {
      super(initialCapacity);
    }

    protected void setSize(int fromIndex) {
      super.removeRange(fromIndex, size());
    }
  }
}
